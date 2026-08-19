package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch

/**
 * Enable screenshots — safe version:
 * - No replaceInstruction("nop") on Window.addFlags/setFlags (that broke all flags + crashed)
 * - Only clears FLAG_SECURE (0x2000) at Activity.onCreate start
 * - Null implementation guard
 */
@Suppress("unused")
val enableScreenshotsPatch = bytecodePatch(
    name = "Enable screenshots",
    description = "Allows screenshots by clearing FLAG_SECURE on Activity onCreate.",
    default = false,
) {
    execute {
        classDefForEach { classDef ->
            val superName = classDef.superclass ?: ""
            val typeName = classDef.type

            // Only Activity subclasses / types
            if (!superName.contains("Activity") && !typeName.contains("Activity")) {
                return@classDefForEach
            }

            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.name != "onCreate") return@forEach
                if (method.returnType != "V") return@forEach

                // ERROR-FREE: skip null implementation
                if (method.instructionsOrNull == null) return@forEach

                // Clear FLAG_SECURE = 0x2000
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
