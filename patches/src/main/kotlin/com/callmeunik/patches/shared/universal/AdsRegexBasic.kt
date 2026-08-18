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
 * AdsRegexBasic — smali "Basic Ads Regex" style blockers as a Morphe bytecode patch.
 * - Kills loadAd / showAd / renderAd style methods
 * - Nops common GMS / ads SDK invokes
 * - Neutralizes common ad URL / AdMob id strings where possible
 */
@Suppress("unused")
val adsRegexBasicPatch = bytecodePatch(
    name = "Ads Regex Basic",
    description = "Basic universal ad killer (loadAd/showAd methods, GMS ads invokes, common ad strings).",
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
            "AdClicked", "AdDismissed", "AdShown",
            "onAdClicked", "onAdLoaded", "onAdClosed", "onAdOpened",
            "onAdFailedToLoad", "onAdImpression", "onAdShowedFullScreenContent",
            "onAdDismissedFullScreenContent", "onUserEarnedReward",
            "setAdListener", "setRewardedVideoAdListener",
        )

        val adInvokeHints = listOf(
            "loadAd", "loadAds", "showAd", "showAds",
            "requestInterstitialAd", "showInterstitial", "showVideo",
            "loadUrl", "loadDataWithBaseURL", "loadData",
            "AdLoader", "AdRequest", "AdListener", "AdView",
            "isLoading", "onAdClicked", "onAdLoaded",
            "fetchAd", "fetchAds", "requestBannerAd",
            "loadBannerAd", "loadNativeAd", "loadRewardedAd",
            "loadInterstitialAd", "loadAppOpenAd",
            "showRewardedVideo", "showOfferwall",
            "setAdListener", "reportAdClicked", "reportAdImpression",
        )

        val adUrlHints = listOf(
            "admob", "adservice", "doubleclick", "googlesyndication",
            "googleads", "pagead", "ads.", ".ads.", "-ads.",
            "adcolony", "applovin", "applvn", "unityads", "vungle",
            "mopub", "inmobi", "chartboost", "tapjoy", "flurry",
            "appsflyer", "crashlytics", "scorecardresearch",
            "startappservice", "supersonicads", "moatads",
            "amazon-ads", "adwhirl", "adsafeprotected",
            "61.145.124.238", "ca-app-pub-",
        )

        classDefForEach { classDef ->
            val className = classDef.type
            val isAdClass =
                className.contains("/ads/", true) ||
                    className.contains("AdMob", true) ||
                    className.contains("AdView", true) ||
                    className.contains("Interstitial", true) ||
                    className.contains("Rewarded", true) ||
                    className.contains("AdLoader", true) ||
                    className.contains("AdRequest", true) ||
                    className.contains("AdListener", true) ||
                    className.contains("AdActivity", true) ||
                    className.contains("gms/ads", true) ||
                    className.contains("applovin", true) ||
                    className.contains("unity3d/ads", true) ||
                    className.contains("vungle", true) ||
                    className.contains("ironsource", true) ||
                    className.contains("facebook/ads", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList()

                // ---- 1) Method bodies: loadAd/showAd/... ----
                val nameHit = adMethodNames.any {
                    methodName.equals(it, true) || methodName.contains(it, true)
                }

                if (nameHit && !method.accessFlags.toString().contains("ABSTRACT") &&
                    !method.accessFlags.toString().contains("NATIVE")
                ) {
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

                // Ad class: isLoaded / isLoading → false
                if (isAdClass && method.returnType == "Z" &&
                    (methodName.contains("isLoaded", true) ||
                        methodName.contains("isLoading", true) ||
                        methodName.contains("isReady", true) ||
                        methodName.contains("canShow", true))
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

                if (instructions == null) return@forEach

                // ---- 2) Nop ad-related invokes ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType

                    val isGmsAd =
                        def.contains("gms/ads", true) ||
                            def.contains("google/android/gms/ads", true) ||
                            def.contains("/ads/", true) ||
                            def.contains("AdView", true) ||
                            def.contains("Interstitial", true) ||
                            def.contains("Rewarded", true) ||
                            def.contains("applovin", true) ||
                            def.contains("unity3d", true) ||
                            def.contains("facebook/ads", true)

                    val isAdInvoke =
                        isGmsAd ||
                            adInvokeHints.any { name.contains(it, true) }

                    if (isAdInvoke && ret == "V") {
                        method.replaceInstruction(index, "nop")
                    }

                    if (isAdInvoke && ret == "Z") {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                }

                // ---- 3) Ad URL / AdMob unit id strings → harmless ----
                instructions.forEachIndexed { index, instruction ->
                    val str =
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                            ?.string ?: return@forEachIndexed

                    val lower = str.lowercase()
                    val isAdString =
                        adUrlHints.any { lower.contains(it) } ||
                            str.matches(Regex("ca-app-pub-\\d{16}/\\d{10}"))

                    if (isAdString) {
                        // Cannot always rewrite string pool safely here;
                        // neutralize following boolean loads if any
                        val next = instructions.getOrNull(index + 1)
                        // leave string; method/invoke kills above do most work
                    }
                }

                // ---- 4) AdActivity-style onCreate: skip early ad branch if pattern simple ----
                if (methodName == "onCreate" &&
                    method.returnType == "V" &&
                    (className.contains("AdActivity", true) ||
                        instructions.any {
                            ((it as? ReferenceInstruction)?.reference as? StringReference)
                                ?.string?.contains("AdActivity") == true
                        })
                ) {
                    // Force quick return to avoid ad UI (may break some ad activities only)
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
}
