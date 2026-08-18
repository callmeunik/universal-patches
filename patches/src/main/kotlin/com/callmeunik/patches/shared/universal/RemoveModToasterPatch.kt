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
 * Remove Mod Toaster
 * 1) Regex-style: nop all Toast.show() / Dialog.show()
 * 2) Extra: kill toasts near Telegram / Mod-by strings
 */
@Suppress("unused")
val removeModToasterPatch = bytecodePatch(
    name = "Remove Mod Toaster",
    description = "Removes Toast/Dialog show calls and common mod APK toast messages (Telegram, t.me, Mod by).",
    default = false,
) {
    execute {
        val toastHints = listOf(
            "telegram", "t.me/", "t.me", "tg://",
            "mod by", "modded by", "cracked by",
            "join channel", "join group", "join us",
            "subscribe", "follow us", "our channel",
            "apkpure", "happymod", "mod apk",
            "premium unlocked", "pro unlocked",
            "credits to", "thanks to", "visit us",
        )

        fun String.hasModToast() = toastHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // =========================================================
                // A) Toaster Regex style
                // invoke-virtual {.*}, Landroid/widget/Toast;->show()V  → nop
                // invoke-virtual {.*}, Landroid/app/Dialog;->show()V    → nop
                // =========================================================
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    val def = ref.definingClass
                    val name = ref.name
                    val ret = ref.returnType

                    // Toast.show()V
                    if (def == "Landroid/widget/Toast;" && name == "show" && ret == "V") {
                        method.replaceInstruction(index, "nop")
                        return@forEachIndexed
                    }

                    // Dialog.show()V
                    if (def == "Landroid/app/Dialog;" && name == "show" && ret == "V") {
                        method.replaceInstruction(index, "nop")
                        return@forEachIndexed
                    }

                    // Subclasses also (AppCompatDialog, AlertDialog, etc.)
                    if (name == "show" && ret == "V" &&
                        (def.contains("Toast") || def.contains("Dialog"))
                    ) {
                        method.replaceInstruction(index, "nop")
                    }
                }

                // =========================================================
                // B) Extra: methods with Telegram / Mod strings
                // =========================================================
                val hasModString = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                        ?: return@any false
                    s.hasModToast()
                }

                if (hasModString) {
                    instructions.forEachIndexed { index, instruction ->
                        val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                        // Toast.makeText → nop + clear move-result-object
                        if (ref.definingClass.contains("Toast") && ref.name == "makeText") {
                            method.replaceInstruction(index, "nop")
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null &&
                                (next.opcode == Opcode.MOVE_RESULT_OBJECT ||
                                    next.opcode == Opcode.MOVE_RESULT)
                            ) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0",
                                )
                            }
                        }
                    }
                }

                // =========================================================
                // C) Method name looks like showModToast / showTelegram
                // =========================================================
                val methodName = method.name
                if (
                    (methodName.contains("toast", true) ||
                        methodName.contains("telegram", true) ||
                        methodName.contains("credit", true) ||
                        methodName.contains("promo", true)) &&
                    (methodName.hasModToast() || methodName.contains("mod", true))
                ) {
                    if (method.returnType == "V") {
                        method.addInstructions(0, "return-void")
                    }
                }
            }
        }
    }
}
