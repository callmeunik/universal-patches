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
 * Universal signature verification killer.
 * Bypasses common PackageManager / SigningInfo / Signature checks.
 *
 * NOTE:
 * - Works on many Java-level checks
 * - Native / server-side signature checks cannot be fully killed by this
 * - Test per app; some apps may still detect re-signing
 */
@Suppress("unused")
val signatureKillPatch = bytecodePatch(
    name = "Signature kill",
    description = "Bypasses common app signature and signing verification checks.",
    default = false,
) {
    execute {
        classDefForEach { classDef ->
            val className = classDef.type

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList()

                // ---- A) Neutralize methods that clearly verify signatures ----
                val looksLikeSigCheck =
                    methodName.contains("signature", true) ||
                        methodName.contains("signing", true) ||
                        methodName.contains("verifySign", true) ||
                        methodName.contains("checkSign", true) ||
                        methodName.contains("validateSign", true) ||
                        methodName.contains("isValidSignature", true) ||
                        methodName.contains("checkIntegrity", true) ||
                        methodName.contains("verifyIntegrity", true) ||
                        methodName.contains("checkCert", true) ||
                        methodName.contains("verifyCert", true)

                if (looksLikeSigCheck && instructions != null) {
                    when (method.returnType) {
                        "Z" -> {
                            // boolean checks → always true (valid)
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x1
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }

                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }

                        "I" -> {
                            // status codes → 0 (ok)
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                // ---- B) Patch PackageManager signature API call-sites ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType
                    val next = instructions.getOrNull(index + 1)

                    // PackageManager.checkSignatures → SIGNATURE_MATCH (0)
                    if (def == "Landroid/content/pm/PackageManager;" &&
                        (name == "checkSignatures" || name == "checkUidSignatures") &&
                        ret == "I"
                    ) {
                        val move = next as? OneRegisterInstruction
                        if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                            // PackageManager.SIGNATURE_MATCH = 0
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${move.registerA}, 0x0",
                            )
                        }
                    }

                    // PackageManager.getPackageInfo — cannot easily fake Signature[] here
                    // but many apps compare hashes after getPackageInfo; covered by method-name kills above

                    // SigningInfo.hasMultipleSigners → false
                    if (def == "Landroid/content/pm/SigningInfo;" &&
                        name == "hasMultipleSigners" &&
                        ret == "Z"
                    ) {
                        val move = next as? OneRegisterInstruction
                        if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${move.registerA}, 0x0",
                            )
                        }
                    }

                    // SigningInfo.hasPastSigningCertificates → false
                    if (def == "Landroid/content/pm/SigningInfo;" &&
                        name == "hasPastSigningCertificates" &&
                        ret == "Z"
                    ) {
                        val move = next as? OneRegisterInstruction
                        if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${move.registerA}, 0x0",
                            )
                        }
                    }

                    // Signature.equals / hash comparisons often custom — method-name kill helps

                    // MessageDigest / cert compare helpers sometimes named verify
                    if ((name.equals("verify", true) || name.equals("verifySignature", true)) &&
                        ret == "Z" &&
                        (def.contains("Signature") ||
                            def.contains("Certificate") ||
                            def.contains("Package") ||
                            className.contains("Security", true) ||
                            className.contains("License", true) ||
                            className.contains("Protect", true) ||
                            className.contains("Integrity", true))
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

        // ---- C) Common wrapper classes: force success on boolean integrity methods ----
        classDefForEach { classDef ->
            val cn = classDef.type
            if (!(cn.contains("Integrity") ||
                    cn.contains("SecurityCheck") ||
                    cn.contains("SignatureCheck") ||
                    cn.contains("Tamper") ||
                    cn.contains("AntiTamper") ||
                    cn.contains("LicenseCheck") ||
                    cn.contains("AppGuard") ||
                    cn.contains("Safety") ||
                    cn.contains("ProtGuard"))
            ) {
                return@classDefForEach
            }

            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.returnType == "Z") {
                    method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x1
                            return v0
                        """.trimIndent(),
                    )
                } else if (method.returnType == "V" &&
                    (method.name.contains("check", true) ||
                        method.name.contains("verify", true) ||
                        method.name.contains("validate", true))
                ) {
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
}
