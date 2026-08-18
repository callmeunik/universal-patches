package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Disable Forced Updates — blocks common version-check / force-update logic.
 */
@Suppress("unused")
val disableForcedUpdatesPatch = bytecodePatch(
    name = "Disable Forced Updates",
    description = "Bypasses common force-update and minimum-version checks.",
    default = false,
) {
    execute {
        val updateHints = listOf(
            "forceupdate", "force_update", "needupdate", "need_update",
            "updateavailable", "update_available", "isupdateavailable",
            "checkupdate", "check_update", "checkforupdate", "check_for_update",
            "minversion", "min_version", "minimumversion", "requiredversion",
            "shouldupdate", "mustupdate", "updateapp", "showupdate",
            "updateprompt", "update_dialog", "outdated", "versioncheck",
        )

        val skipParts = listOf("log", "track", "analytics", "report", "error")

        fun String.hasUpdateHint() = updateHints.any { contains(it, ignoreCase = true) }
        fun String.shouldSkip() = skipParts.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                // Method itself looks like update check → force false / return-void
                if (!name.shouldSkip() && name.hasUpdateHint()) {
                    when (method.returnType) {
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x0
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

                // Call-sites that return boolean
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (ref.name.shouldSkip()) return@forEachIndexed
                    if (!ref.name.hasUpdateHint() && !ref.definingClass.hasUpdateHint()) return@forEachIndexed

                    when (ref.returnType) {
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0"
                                )
                            }
                        }
                        "V" -> method.replaceInstruction(index, "nop")
                    }
                }

                // Common string-based version check
                val hasVersionString = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                        ?: return@any false
                    s.contains("update", true) || s.contains("version", true) ||
                        s.contains("outdated", true) || s.contains("force update", true)
                }

                if (hasVersionString) {
                    instructions.forEachIndexed { index, instruction ->
                        val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed
                        if (ref.returnType == "Z") {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
