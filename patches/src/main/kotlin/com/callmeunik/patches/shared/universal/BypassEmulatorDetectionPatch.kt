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
 * Bypass Emulator Detection
 * Soft-bypasses common emulator / Genymotion / BlueStacks / Android Studio checks.
 */
@Suppress("unused")
val bypassEmulatorDetectionPatch = bytecodePatch(
    name = "Bypass Emulator Detection",
    description = "Bypasses common emulator, Genymotion, BlueStacks and virtual device detection checks.",
    default = false,
) {
    execute {
        val emulatorHints = listOf(
            "isemulator", "is_emulator", "isvirtual", "is_virtual",
            "isgenymotion", "is_genymotion", "isbluestacks", "is_bluestacks",
            "isnox", "is_nox", "ismemu", "is_memu", "isldplayer",
            "isandy", "is_andy", "isvirtualbox", "is_virtualbox",
            "isqemu", "is_qemu", "isgoldfish", "is_goldfish",
            "isrunningonemulator", "is_running_on_emulator",
            "checkemulator", "detectemulator", "emulatorcheck",
            "isgeny", "is_geny", "isfake", "is_fake",
        )

        val emulatorStrings = listOf(
            "generic", "unknown", "google_sdk", "emulator", "android sdk built for x86",
            "genymotion", "bluestacks", "nox", "memu", "ldplayer",
            "andy", "ttvm", "vbox", "virtualbox", "qemu", "goldfish",
            "ranchu", "sdk_gphone", "sdk_google", "google_sdk",
            "droid4x", "tiantian", "windroye", "microvirt",
        )

        fun String.hasEmulatorHint() = emulatorHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                // Method name looks like emulator check → force false
                if (name.hasEmulatorHint()) {
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

                // Call-sites
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (!ref.name.hasEmulatorHint() && !ref.definingClass.hasEmulatorHint()) return@forEachIndexed

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

                // String-based detection → soften nearby boolean results
                val hasEmuString = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                        ?: return@any false
                    emulatorStrings.any { e -> s.contains(e, ignoreCase = true) }
                }

                if (hasEmuString) {
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
