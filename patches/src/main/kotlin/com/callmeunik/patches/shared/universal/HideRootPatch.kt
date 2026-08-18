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
 * Hides common root / Magisk / su traces from Java-level detection.
 */
@Suppress("unused")
val hideRootPatch = bytecodePatch(
    name = "Hide root",
    description = "Bypasses common root, Magisk and su detection checks.",
    default = false,
) {
    execute {
        val rootStrings = setOf(
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "magisk",
            "Magisk",
            "/data/adb/magisk",
            "com.topjohnwu.magisk",
            "supersu",
            "Superuser",
            "busybox",
            "test-keys",
            "which su",
            "ro.debuggable",
            "ro.secure",
        )

        classDefForEach { classDef ->
            val cn = classDef.type
            val isRootDetectorClass =
                cn.contains("Root", true) ||
                    cn.contains("Magisk", true) ||
                    cn.contains("BusyBox", true) ||
                    cn.contains("SuperUser", true) ||
                    cn.contains("Jailbreak", true) ||
                    cn.contains("Safety", true) ||
                    cn.contains("SecurityCheck", true) ||
                    cn.contains("DeviceCheck", true) ||
                    cn.contains("Integrity", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                // A) Methods clearly about root → force safe result
                val isRootMethod =
                    name.contains("isRoot", true) ||
                        name.contains("checkRoot", true) ||
                        name.contains("detectRoot", true) ||
                        name.contains("hasRoot", true) ||
                        name.contains("isDeviceRooted", true) ||
                        name.contains("isMagisk", true) ||
                        name.contains("checkMagisk", true) ||
                        name.contains("isBusyBox", true) ||
                        name.contains("isSu", true) ||
                        name.contains("findBinary", true) ||
                        name.contains("checkSu", true)

                if (isRootMethod || isRootDetectorClass) {
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
                            if (name.contains("check", true) || name.contains("detect", true)) {
                                method.addInstructions(0, "return-void")
                                return@forEach
                            }
                        }
                        "I" -> {
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

                // B) File.exists / Runtime.exec call sites near root paths — weaken boolean results after contains su/magisk strings in same method
                val methodHasRootString = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                    s != null && rootStrings.any { r -> s.contains(r, true) }
                }

                if (methodHasRootString) {
                    instructions.forEachIndexed { index, instruction ->
                        val reference =
                            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                                ?: return@forEachIndexed

                        // File.exists() → false when used in root checks
                        if (reference.definingClass == "Ljava/io/File;" &&
                            reference.name == "exists" &&
                            reference.returnType == "Z"
                        ) {
                            val move = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${move.registerA}, 0x0",
                                )
                            }
                        }

                        // Runtime.exec — leave invoke, detection methods already neutered
                    }
                }
            }
        }
    }
}
