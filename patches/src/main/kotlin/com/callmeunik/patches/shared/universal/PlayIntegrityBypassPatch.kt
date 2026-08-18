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
 * Play Integrity / SafetyNet Bypass (Basic)
 * Soft-bypasses common integrity / attestation result checks.
 */
@Suppress("unused")
val playIntegrityBypassPatch = bytecodePatch(
    name = "Play Integrity Bypass",
    description = "Basic bypass for common Play Integrity / SafetyNet / attestation checks.",
    default = false,
) {
    execute {
        val integrityHints = listOf(
            "safetynet", "safety_net", "playintegrity", "play_integrity",
            "integrity", "attestation", "attest", "ctsprofile",
            "basicintegrity", "basic_integrity", "deviceintegrity",
            "isrooted", "is_rooted", "isemulator", "is_emulator",
            "isdebuggable", "is_debuggable", "checkintegrity",
            "verifyintegrity", "integritycheck", "nonce",
        )

        fun String.hasIntegrity() = integrityHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                if (name.hasIntegrity()) {
                    when (method.returnType) {
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x1
                                return v0
                            """.trimIndent())
                            return@forEach
                        }
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (!ref.name.hasIntegrity() && !ref.definingClass.hasIntegrity()) return@forEachIndexed

                    when (ref.returnType) {
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x1"
                                )
                            }
                        }
                        "V" -> method.replaceInstruction(index, "nop")
                    }
                }
            }
        }
    }
}
