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
 * Ads Regex Basic — fixed:
 * - null implementation guard
 * - no replaceInstruction("nop")
 * - loadAd/showAd method early-return + boolean force only
 */
@Suppress("unused")
val adsRegexBasicPatch = bytecodePatch(
    name = "Ads Regex Basic",
    description = "Basic universal ad killer (loadAd/showAd methods, GMS ads invokes).",
    default = false,
) {
    execute {
        val adMethodNames = setOf(
            "loadAd", "loadAds", "showAd", "showAds", "renderAd",
            "requestInterstitialAd", "showInterstitial", "showInterstitialAd",
            "showVideo", "showVideoAd", "showRewardedVideo", "showRewardedVideoAd",
            "showBannerAd", "showNativeAd", "showOfferwall",
            "requestBannerAd", "requestNativeAd",
            "loadBannerAd", "loadNativeAd", "loadRewardedAd",
            "loadRewardedInterstitialAd", "loadInterstitialAd", "loadAppOpenAd",
            "loadRewardedVideo", "loadNextAd", "loadSmartBanner",
            "fetchAd", "fetchAds", "createInterstitialAd",
            "setNativeAd", "unsetNativeAd",
        )

        val adInvokeHints = listOf(
            "loadAd", "loadAds", "showAd", "showAds",
            "requestInterstitialAd", "showInterstitial", "showVideo",
            "fetchAd", "fetchAds", "requestBannerAd",
            "loadBannerAd", "loadNativeAd", "loadRewardedAd",
            "loadInterstitialAd", "loadAppOpenAd",
            "showRewardedVideo", "showOfferwall",
            "isLoaded", "isLoading", "isReady", "canShow",
        )

        fun String.isAdSdkClass(): Boolean =
            contains("/ads/", true) ||
                contains("AdMob", true) ||
                contains("AdView", true) ||
                contains("Interstitial", true) ||
                contains("Rewarded", true) ||
                contains("AdLoader", true) ||
                contains("AdRequest", true) ||
                contains("AdActivity", true) ||
                contains("gms/ads", true) ||
                contains("applovin", true) ||
                contains("unity3d/ads", true) ||
                contains("vungle", true) ||
                contains("ironsource", true) ||
                contains("facebook/ads", true) ||
                contains("adcolony", true) ||
                contains("chartboost", true)

        classDefForEach { classDef ->
            val className = classDef.type
            val isAdClass = className.isAdSdkClass()

            mutableClassDefBy(classDef).methods.forEach { method ->
                // FIX: never touch null implementation
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                val nameHit = adMethodNames.any {
                    methodName.equals(it, true) || methodName.contains(it, true)
                }

                // ---- 1) loadAd / showAd style methods ----
                if (nameHit) {
                    when (method.returnType) {
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
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
                    }
                }

                // ---- 2) Ad SDK class: isLoaded / isReady → false ----
                if (isAdClass && method.returnType == "Z" &&
                    (methodName.contains("isLoaded", true) ||
                        methodName.contains("isLoading", true) ||
                        methodName.contains("isReady", true) ||
                        methodName.contains("canShow", true) ||
                        methodName.contains("isShowing", true))
                ) {
                    method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent(),
                    )
                    return@forEach
                }

                // ---- 3) Call-sites: only boolean force — NO nop ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType

                    val isAdInvoke =
                        def.isAdSdkClass() ||
                            adInvokeHints.any { name.contains(it, true) }

                    if (!isAdInvoke) return@forEachIndexed

                    if (ret == "Z") {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                    // Void invokes: do NOT use replaceInstruction(..., "nop")
                }

                // ---- 4) AdActivity onCreate only (class name check) ----
                if (methodName == "onCreate" &&
                    method.returnType == "V" &&
                    className.contains("AdActivity", true)
                ) {
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
}
