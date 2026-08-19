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
                if (method.instructionsOrNull == null) return@forEach

                val name = method.name
                val instructions = method.instructionsOrNull!!.toList()

                if (!name.shouldSkip() && name.hasUpdateHint()) {
                    when (method.returnType) {
                        "Z" -> {
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

                // Call-sites: boolean only — NO nop
                instructions.forEachIndexed { index, instruction ->
                    val ref =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    if (ref.name.shouldSkip()) return@forEachIndexed
                    if (!ref.name.hasUpdateHint() && !ref.definingClass.hasUpdateHint()) {
                        return@forEachIndexed
                    }

                    if (ref.returnType == "Z") {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                }

                val hasVersionString = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                        ?: return@any false
                    val l = s.lowercase()
                    l.contains("update available") ||
                        l.contains("force update") ||
                        l.contains("please update") ||
                        l.contains("new version") ||
                        l.contains("minimum version")
                }

                if (hasVersionString) {
                    instructions.forEachIndexed { index, instruction ->
                        val ref =
                            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                                ?: return@forEachIndexed
                        if (ref.returnType != "Z") return@forEachIndexed
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                }
            }
        }
    }
}
