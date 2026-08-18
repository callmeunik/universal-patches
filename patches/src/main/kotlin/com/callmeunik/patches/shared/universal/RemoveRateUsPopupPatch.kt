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
 * Remove Rate Us / In-App Review Popup
 */
@Suppress("unused")
val removeRateUsPopupPatch = bytecodePatch(
    name = "Remove Rate Us Popup",
    description = "Blocks common Rate Us / In-App Review dialogs and prompts.",
    default = false,
) {
    execute {
        val rateHints = listOf(
            "rateus", "rate_us", "rateapp", "rate_app",
            "inappreview", "in_app_review", "appreview", "app_review",
            "showrate", "show_rate", "requestrate", "request_rate",
            "launchreview", "launch_review", "reviewflow", "review_flow",
            "ratingdialog", "rating_dialog", "feedbackdialog",
            "askforreview", "ask_for_review", "promptrating",
        )

        fun String.hasRateHint() = rateHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                if (name.hasRateHint()) {
                    when (method.returnType) {
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x0
                                return v0
                            """.trimIndent())
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (!ref.name.hasRateHint() && !ref.definingClass.hasRateHint()) return@forEachIndexed

                    when (ref.returnType) {
                        "V" -> method.replaceInstruction(index, "nop")
                        "Z" -> {
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
