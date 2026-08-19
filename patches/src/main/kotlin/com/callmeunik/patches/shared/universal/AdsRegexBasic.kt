package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * AdsRegexBasic — fixed: skip methods with null implementation.
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

        fun hasImpl(method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod): Boolean {
            return method.instructionsOrNull != null
        }

        fun isAbstractOrNative(accessFlags: Int): Boolean {
            return AccessFlags.ABSTRACT.isSet(accessFlags) ||
                AccessFlags.NATIVE.isSet(accessFlags)
        }

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
                // CRITICAL FIX: never touch methods without implementation
                if (isAbstractOrNative(method.accessFlags)) return@forEach
                if (!hasImpl(method)) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                val nameHit = adMethodNames.any {
                    methodName.equals(it, true) || methodName.contains(it, true)
                }

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
                        isGmsAd || adInvokeHints.any { name.contains(it, true) }

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

                // Safer AdActivity handling: only early-return if class name is AdActivity
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
