package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Unlock Premium — fixed:
 * - null implementation guard
 * - no replaceInstruction("nop")
 * - safer hint matching (less false positives)
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "This patch can Unlock Premium, VIP, Pro, Gold, Subscription for Some App.",
    default = false,
) {
    execute {
        val premiumHints = listOf(
            "ispremium", "is_premium", "haspremium", "has_premium",
            "premiumuser", "premium_user", "premiumenabled", "premium_enabled",
            "ispro", "is_pro", "proversion", "pro_version", "prouser", "pro_user",
            "isvip", "is_vip", "vipmember", "vip_member", "vipuser", "vip_user",
            "isgold", "is_gold", "goldmember", "gold_member",
            "ispurchased", "is_purchased", "haspurchased", "has_purchased",
            "issubscribed", "is_subscribed", "hassubscription", "has_subscription",
            "subscriptionactive", "subscription_active",
            "isunlocked", "is_unlocked", "unlockpremium", "unlock_premium",
            "hasaccess", "has_access", "fullaccess", "full_access",
            "isadfree", "is_ad_free", "adfree", "ad_free", "removeads", "remove_ads",
            "ismember", "is_member", "iselite", "is_elite",
            "islifetime", "is_lifetime", "isplus", "is_plus",
            "ispremiumuser", "go_premium", "gopremium",
        )

        // Field-only extra (shorter names ok on fields)
        val fieldHints = premiumHints + listOf(
            "premium", "subscribed", "purchased", "unlocked",
            "isPaid", "isPro", "isVip", "isGold", "isPlus",
        )

        fun String.hasPremiumHint(): Boolean {
            val lower = lowercase()
            return premiumHints.any { lower.contains(it) }
        }

        fun String.hasFieldHint(): Boolean {
            val lower = lowercase()
            return fieldHints.any { lower.contains(it) }
        }

        fun String.shouldSkip(): Boolean {
            val lower = lowercase()
            return lower.contains("error") ||
                lower.contains("exception") ||
                lower.contains("log") ||
                lower.contains("debug") ||
                lower.contains("throw") ||
                lower.contains("process") || // avoid "pro" inside process
                lower.contains("product") ||
                lower.contains("progress") ||
                lower.contains("protocol") ||
                lower.contains("provide")
        }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                // FIX: never touch methods without implementation
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                // 1) isPremium / isPro / ... ()Z → return true
                if (method.returnType == "Z" &&
                    methodName.hasPremiumHint() &&
                    !methodName.shouldSkip()
                ) {
                    method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x1
                            return v0
                        """.trimIndent(),
                    )
                    return@forEach
                }

                // 2) Field boolean reads: iget-boolean / sget-boolean
                instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.IGET_BOOLEAN &&
                        instruction.opcode != Opcode.SGET_BOOLEAN
                    ) {
                        return@forEachIndexed
                    }

                    val fieldRef =
                        (instruction as? ReferenceInstruction)?.reference as? FieldReference
                            ?: return@forEachIndexed

                    if (fieldRef.type != "Z") return@forEachIndexed
                    if (!fieldRef.name.hasFieldHint()) return@forEachIndexed
                    if (fieldRef.name.shouldSkip()) return@forEachIndexed

                    val reg = when (instruction) {
                        is TwoRegisterInstruction -> instruction.registerA
                        is OneRegisterInstruction -> instruction.registerA
                        else -> return@forEachIndexed
                    }

                    method.replaceInstruction(
                        index,
                        "const/4 v$reg, 0x1",
                    )
                }

                // 3) SharedPreferences / JSON getBoolean after premium string
                instructions.forEachIndexed { index, instruction ->
                    val str =
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                            ?.string ?: return@forEachIndexed

                    if (!str.hasPremiumHint() && !str.hasFieldHint()) return@forEachIndexed

                    for (offset in 1..8) {
                        val invoke = instructions.getOrNull(index + offset) ?: break
                        val ref =
                            (invoke as? ReferenceInstruction)?.reference as? MethodReference
                                ?: continue

                        if (ref.name != "getBoolean" && ref.name != "getBool") continue
                        if (ref.returnType != "Z") continue

                        val move =
                            instructions.getOrNull(index + offset + 1) as? OneRegisterInstruction
                                ?: continue

                        if (move.opcode == Opcode.MOVE_RESULT ||
                            move.opcode == Opcode.MOVE_RESULT_OBJECT
                        ) {
                            method.replaceInstruction(
                                index + offset + 1,
                                "const/4 v${move.registerA}, 0x1",
                            )
                        }
                    }
                }

                // 4) Call-sites: invoke isPremium() → force true
                instructions.forEachIndexed { index, instruction ->
                    val ref =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    if (ref.returnType != "Z") return@forEachIndexed
                    if (!ref.name.hasPremiumHint()) return@forEachIndexed
                    if (ref.name.shouldSkip()) return@forEachIndexed

                    val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        ?: return@forEachIndexed

                    if (next.opcode == Opcode.MOVE_RESULT ||
                        next.opcode == Opcode.MOVE_RESULT_OBJECT
                    ) {
                        method.replaceInstruction(
                            index + 1,
                            "const/4 v${next.registerA}, 0x1",
                        )
                    }
                }
            }
        }
    }
}
