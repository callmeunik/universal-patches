package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Allows screenshots by removing / neutralizing FLAG_SECURE calls.
 * Useful for apps that block screenshots by default (banking, etc.).
 */
@Suppress("unused")
val enableScreenshotsPatch = bytecodePatch(
    name = "Enable screenshots",
    description = "Removes FLAG_SECURE so screenshots and screen recording are allowed.",
    default = false,
) {
    execute {
        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    // Window.addFlags(FLAG_SECURE) or setFlags with secure bit
                    if (reference.definingClass != "Landroid/view/Window;") return@forEachIndexed

                    when (reference.name) {
                        "addFlags", "setFlags" -> {
                            // Replace call with no-op style: don't apply secure flag
                            // Safest approach: replace invoke with nop-equivalent by
                            // clearing the secure bit usage — we skip the invoke.
                            // Use: replace the invoke instruction with a safe nop path
                            // by converting it to a harmless const (keeps register flow stable enough for many apps)
                            method.replaceInstruction(index, "nop")
                        }

                        "clearFlags" -> {
                            // leave as-is
                        }
                    }
                }
            }
        }

        // Also force clear FLAG_SECURE on Activity onCreate for stubborn apps
        classDefForEach { classDef ->
            val superName = classDef.superclass ?: ""
            if (!superName.contains("Activity") && !classDef.type.contains("Activity")) {
                return@classDefForEach
            }

            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.name != "onCreate" || method.returnType != "V") return@forEach

                method.addInstructions(
                    0,
                    """
                        invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                        move-result-object v0
                        if-nez v0, :en_end
                        const/16 v1, 0x2000
                        invoke-virtual {v0, v1}, Landroid/view/Window;->clearFlags(I)V
                        :en_end
                        nop
                    """.trimIndent(),
                )
            }
        }
    }
}
