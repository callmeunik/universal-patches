package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Blocks screenshots and screen recording by applying FLAG_SECURE (0x2000)
 * at the start of Activity onCreate.
 */
@Suppress("unused")
val disableScreenshotsPatch = bytecodePatch(
    name = "Disable screenshots",
    description = "Blocks screenshots and screen recording using FLAG_SECURE on Activity onCreate.",
    default = false,
) {
    execute {
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
                        if-nez v0, :sec_end
                        const/16 v1, 0x2000
                        invoke-virtual {v0, v1}, Landroid/view/Window;->addFlags(I)V
                        :sec_end
                        nop
                    """.trimIndent(),
                )
            }
        }
    }
}
