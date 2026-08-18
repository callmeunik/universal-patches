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
 * Unlock Premium / VIP / Pro / Gold / Subscription
 * This patch can Unlock Premium, VIP, Pro, Gold, Subscription for Some App.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "This patch can Unlock Premium, VIP, Pro, Gold, Subscription for Some App.",
    default = false,
) {
    execute {
        val premiumHints = listOf(
            // Core
            "premium", "ispremium", "is_premium", "haspremium", "has_premium",
            "pro", "ispro", "is_pro", "proversion", "pro_version",
            "vip", "isvip", "is_vip", "vipmember", "vip_member",
            "gold", "isgold", "is_gold", "goldmember",
            "paid", "ispaid", "is_paid", "purchased", "ispurchased", "is_purchased",
            "owned", "isowned", "is_owned",
            // Subscription
            "subscri", "subscribed", "issubscribed", "is_subscribed",
            "subscription", "hassubscription", "has_subscription",
            "active_subscription", "subscription_active", "subscription_status",
            // Access / unlock
            "unlocked", "isunlocked", "is_unlocked", "unlockpremium", "unlock_premium",
            "hasaccess", "has_access", "fullaccess", "full_access",
            "adfree", "ad_free", "isadfree", "is_ad_free", "noads", "no_ads", "removeads", "remove_ads",
            // Membership
            "member", "ismember", "is_member", "membership",
            "elite", "iselite", "is_elite",
            "lifetime", "islifetime", "is_lifetime",
            "plus", "isplus", "is_plus",
            // Billing flags
            "iap", "iab", "sku", "onetime", "one_time",
            "donated", "isdonated", "trial", "istrial",
            "activated", "isactivated", "is_activated",
            "eligible", "iseligible", "is_eligible",
            "valid", "isvalid", "is_valid",
            "active", "isactive", "is_active",
            // Extra common
            "allowemojisfornonpremium", "isadsdisabled", "ispremiumuser",
            "purchaseflag", "gopremium", "go_premium",
            "platinum", "diamond", "prime",
        )

        fun String.hasPremiumHint(): Boolean {
            val lower = lowercase()
            return premiumHints.any { lower.contains(it) }
        }

        // Skip dangerous names
        fun String.shouldSkip(): Boolean {
            val lower = lowercase()
            return lower.contains("error") ||
                lower.contains("exception") ||
                lower.contains("log") ||
                lower.contains("debug") ||
                lower.contains("throw")
        }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList()

                // =========================================================
                // 1) Method body rewrite
                // isPremium / isPro / hasSubscription / isVip ... ()Z → return true
                // =========================================================
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

                if (instructions == null) return@forEach

                // =========================================================
                // 2) Field boolean reads
                // iget-boolean / sget-boolean → *Premium* / *Pro* / *Vip* :Z
                // force register = 1
                // =========================================================
                instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.IGET_BOOLEAN &&
                        instruction.opcode != Opcode.SGET_BOOLEAN
                    ) {
                        return@forEachIndexed
                    }

                    val fieldRef = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                        ?: return@forEachIndexed

                    if (fieldRef.type != "Z") return@forEachIndexed
                    if (!fieldRef.name.hasPremiumHint()) return@forEachIndexed

                    val reg = when (instruction) {
                        is TwoRegisterInstruction -> instruction.registerA  // iget-boolean
                        is OneRegisterInstruction -> instruction.registerA  // sget-boolean
                        else -> return@forEachIndexed
                    }

                    method.replaceInstruction(
                        index,
                        "const/4 v$reg, 0x1",
                    )
                }

                // =========================================================
                // 3) SharedPreferences / getBoolean("is_premium") style
                // const-string "premium..." → invoke getBoolean → move-result → force true
                // =========================================================
                instructions.forEachIndexed { index, instruction ->
                    val str = ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                        ?.string ?: return@forEachIndexed

                    if (!str.hasPremiumHint()) return@forEachIndexed

                    // Look ahead for getBoolean + move-result
                    for (offset in 1..8) {
                        val invoke = instructions.getOrNull(index + offset) ?: break
                        val ref = (invoke as? ReferenceInstruction)?.reference as? MethodReference
                            ?: continue

                        if (ref.name != "getBoolean" && ref.name != "getBool") continue
                        if (ref.returnType != "Z") continue

                        val move = instructions.getOrNull(index + offset + 1) as? OneRegisterInstruction
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

                // =========================================================
                // 4) Call-sites: invoke isPremium() / isPro() → move-result force true
                // =========================================================
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
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
