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
 * Play Integrity Bypass — fixed:
 * - no replaceInstruction("nop")
 * - null implementation guard
 * - boolean force + method early-return only
 */
@Suppress("unused")
val playIntegrityBypassPatch = bytecodePatch(
    name = "Play Integrity Bypass",
    description = "Basic bypass for common Play Integrity / SafetyNet / attestation checks.",
    default = false,
) {
    execute {
        val integrityHints = listOf(
            "safetynet", "safety_net",
            "playintegrity", "play_integrity",
            "attestation", "attest",
            "ctsprofile", "cts_profile",
            "basicintegrity", "basic_integrity",
            "deviceintegrity", "device_integrity",
            "checkintegrity", "verifyintegrity", "integritycheck",
            "isrooted", "is_rooted",
            "isemulator", "is_emulator",
            "isdebuggable", "is_debuggable",
        )

        val skipParts = listOf(
            "log", "debug", "error", "exception", "throw", "toString",
        )

        fun String.hasIntegrity(): Boolean {
            val lower = lowercase().replace("_", "")
            return integrityHints.any { lower.contains(it.replace("_", "")) }
        }

        fun String.shouldSkip() =
            skipParts.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                // ERROR-FREE
                if (method.instructionsOrNull == null) return@forEach

                val name = method.name
                if (name.shouldSkip()) return@forEach

                val instructions = method.instructionsOrNull!!.toList()

                // ---- 1) Method name looks like integrity check ----
                if (name.hasIntegrity()) {
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
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                // ---- 2) Call-sites: only boolean force — NO nop ----
                instructions.forEachIndexed { index, instruction ->
                    val ref =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    if (ref.name.shouldSkip()) return@forEachIndexed
                    if (!ref.name.hasIntegrity() && !ref.definingClass.hasIntegrity()) {
                        return@forEachIndexed
                    }

                    if (ref.returnType == "Z") {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x1",
                            )
                        }
                    }
                    // Void invokes: do NOT use replaceInstruction(..., "nop")
                }
            }
        }
    }
}
