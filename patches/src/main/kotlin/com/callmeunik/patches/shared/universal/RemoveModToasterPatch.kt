package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Remove Mod Toaster — fixed:
 * - No replaceInstruction("nop") (Morphe smali crash)
 * - Null-safe
 * - Kills methods that show mod / Telegram / channel promo toasts
 */
@Suppress("unused")
val removeModToasterPatch = bytecodePatch(
    name = "Remove Mod Toaster",
    description = "Removes common mod APK toast / promo dialogs (Telegram, t.me, Mod by, channel joins).",
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
            "modded apk", "patched by",
        )

        fun String.hasModToast() =
            toastHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                // Null implementation guard
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                // ---- A) Method name looks like mod / promo toast ----
                val nameLooksPromo =
                    methodName.contains("toast", true) ||
                        methodName.contains("telegram", true) ||
                        methodName.contains("credit", true) ||
                        methodName.contains("promo", true) ||
                        methodName.contains("channel", true) ||
                        methodName.contains("banner", true) ||
                        methodName.contains("announce", true)

                if (method.returnType == "V" &&
                    nameLooksPromo &&
                    (methodName.hasModToast() ||
                        methodName.contains("mod", true) ||
                        methodName.contains("telegram", true) ||
                        methodName.contains("credit", true))
                ) {
                    method.addInstructions(0, "return-void")
                    return@forEach
                }

                // ---- B) Method body contains mod / telegram strings ----
                val hasModString = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                        ?: return@any false
                    s.hasModToast()
                }

                if (!hasModString) return@forEach

                // Check if method actually shows Toast or Dialog
                val showsUi = instructions.any {
                    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@any false
                    val def = ref.definingClass
                    val name = ref.name
                    (def.contains("Toast") && (name == "show" || name == "makeText")) ||
                        (def.contains("Dialog") && name == "show") ||
                        (def.contains("Snackbar") && name == "show") ||
                        (def.contains("AlertDialog") && (name == "show" || name == "create"))
                }

                // Void method that builds mod toast/dialog → kill entire method
                if (method.returnType == "V" && showsUi) {
                    method.addInstructions(0, "return-void")
                    return@forEach
                }

                // Named show* with mod strings
                if (method.returnType == "V" &&
                    (methodName.startsWith("show") ||
                        methodName.contains("display", true) ||
                        methodName.contains("popup", true))
                ) {
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
}
