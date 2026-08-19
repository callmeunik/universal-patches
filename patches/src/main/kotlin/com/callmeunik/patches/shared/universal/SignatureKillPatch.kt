package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Signature kill — powerful, safe, error-free:
 * - null implementation guard (no NPE)
 * - no replaceInstruction("nop")
 * - targeted signature / integrity method names
 * - PackageManager + SigningInfo call-site bypass
 * - NO blind kill of every boolean in "Safety" classes
 */
@Suppress("unused")
val signatureKillPatch = bytecodePatch(
    name = "Signature kill",
    description = "Bypasses common app signature, signing and integrity verification checks.",
    default = false,
) {
    execute {
        val sigMethodHints = listOf(
            "checksignature", "check_signature", "checksignatures",
            "verifysignature", "verify_signature", "verifysign",
            "validatesignature", "validate_signature", "validatesign",
            "isvalidsignature", "is_valid_signature",
            "signaturevalid", "signature_valid",
            "checksigning", "verifysigning",
            "checkcert", "verifycert", "validatecert",
            "checkcertificate", "verifycertificate",
            "checkintegrity", "verifyintegrity", "validateintegrity",
            "integritycheck", "integrity_check",
            "istampered", "is_tampered", "checktamper", "detecttamper",
            "isapkmodified", "is_apk_modified", "ismodified",
            "checkpkg", "verifypackage",
            "originalsignature", "appsignature",
        )

        val skipParts = listOf(
            "log", "debug", "error", "exception", "throw",
            "toString", "hashCode", "equals", "compare",
        )

        fun String.hasSigHint(): Boolean {
            val lower = lowercase().replace("_", "")
            return sigMethodHints.any { lower.contains(it.replace("_", "")) }
        }

        fun String.shouldSkip(): Boolean =
            skipParts.any { contains(it, ignoreCase = true) }

        fun String.isSigGuardClass(): Boolean {
            val c = this
            return c.contains("SignatureCheck", true) ||
                c.contains("SignatureVerifier", true) ||
                c.contains("IntegrityCheck", true) ||
                c.contains("IntegrityGuard", true) ||
                c.contains("TamperDetect", true) ||
                c.contains("AntiTamper", true) ||
                c.contains("AppGuard", true) ||
                c.contains("ProtGuard", true) ||
                c.contains("SecurityCheck", true) ||
                c.contains("LicenseCheck", true) ||
                (c.contains("Signature", true) && c.contains("Util", true)) ||
                (c.contains("Signature", true) && c.contains("Helper", true))
        }

        classDefForEach { classDef ->
            val className = classDef.type
            val isGuardClass = className.isSigGuardClass()

            mutableClassDefBy(classDef).methods.forEach { method ->
                // ERROR-FREE: never touch null implementation
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                if (methodName.shouldSkip()) return@forEach

                val instructions = method.instructionsOrNull!!.toList()
                val looksLikeSig =
                    methodName.hasSigHint() ||
                        (isGuardClass &&
                            (methodName.contains("check", true) ||
                                methodName.contains("verify", true) ||
                                methodName.contains("validate", true) ||
                                methodName.contains("sign", true) ||
                                methodName.contains("integrity", true) ||
                                methodName.contains("tamper", true)))

                // ---- A) Method body rewrite ----
                if (looksLikeSig) {
                    when (method.returnType) {
                        "Z" -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x1
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                        "I" -> {
                            // PackageManager.SIGNATURE_MATCH = 0
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                // ---- B) Call-sites (no nop) ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType
                    val next = instructions.getOrNull(index + 1)

                    // PackageManager.checkSignatures / checkUidSignatures → 0 (MATCH)
                    if (def == "Landroid/content/pm/PackageManager;" &&
                        (name == "checkSignatures" || name == "checkUidSignatures") &&
                        ret == "I"
                    ) {
                        val move = next as? OneRegisterInstruction
                        if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${move.registerA}, 0x0",
                            )
                        }
                    }

                    // SigningInfo helpers
                    if (def == "Landroid/content/pm/SigningInfo;" && ret == "Z") {
                        if (name == "hasMultipleSigners" ||
                            name == "hasPastSigningCertificates"
                        ) {
                            val move = next as? OneRegisterInstruction
                            if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${move.registerA}, 0x0",
                                )
                            }
                        }
                    }

                    // Custom verify / checkSignature call-sites → true
                    if (ret == "Z" &&
                        name.hasSigHint() &&
                        !name.shouldSkip()
                    ) {
                        val move = next as? OneRegisterInstruction
                        if (move != null &&
                            (move.opcode == Opcode.MOVE_RESULT ||
                                move.opcode == Opcode.MOVE_RESULT_OBJECT)
                        ) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${move.registerA}, 0x1",
                            )
                        }
                    }

                    // Signature.equals often used for compare — force true when in guard context
                    if (def == "Landroid/content/pm/Signature;" &&
                        name == "equals" &&
                        ret == "Z" &&
                        (className.isSigGuardClass() ||
                            className.contains("Signature", true) ||
                            className.contains("Security", true) ||
                            className.contains("License", true) ||
                            className.contains("Protect", true))
                    ) {
                        val move = next as? OneRegisterInstruction
                        if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${move.registerA}, 0x1",
                            )
                        }
                    }
                }
            }
        }
    }
}
