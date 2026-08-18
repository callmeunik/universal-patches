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
 * Spoofs installer package as Google Play Store (com.android.vending).
 */
@Suppress("unused")
val spoofInstallSourcePatch = bytecodePatch(
    name = "Spoof install source",
    description = "Makes the app think it was installed from Google Play Store.",
    default = false,
) {
    execute {
        val playStore = "com.android.vending"

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType
                    val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction

                    // PackageManager.getInstallerPackageName → "com.android.vending"
                    if (def == "Landroid/content/pm/PackageManager;" &&
                        name == "getInstallerPackageName" &&
                        ret == "Ljava/lang/String;"
                    ) {
                        if (next != null && next.opcode == Opcode.MOVE_RESULT_OBJECT) {
                            val r = next.registerA
                            method.addInstructions(
                                index + 2,
                                """
                                    const-string v$r, "$playStore"
                                """.trimIndent(),
                            )
                        }
                    }

                    // InstallSourceInfo.getInstallingPackageName (API 30+)
                    if (def == "Landroid/content/pm/InstallSourceInfo;" &&
                        (name == "getInstallingPackageName" ||
                            name == "getInitiatingPackageName" ||
                            name == "getOriginatingPackageName") &&
                        ret == "Ljava/lang/String;"
                    ) {
                        if (next != null && next.opcode == Opcode.MOVE_RESULT_OBJECT) {
                            val r = next.registerA
                            method.addInstructions(
                                index + 2,
                                """
                                    const-string v$r, "$playStore"
                                """.trimIndent(),
                            )
                        }
                    }
                }

                // Named helpers
                val mn = method.name
                if ((mn.contains("getInstaller", true) ||
                        mn.contains("installSource", true) ||
                        mn.contains("installerPackage", true)) &&
                    method.returnType == "Ljava/lang/String;"
                ) {
                    method.addInstructions(
                        0,
                        """
                            const-string v0, "$playStore"
                            return-object v0
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
