package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch

/**
 * Ads Regex Advance — LOW MEMORY
 * Only touches ad-SDK classes. No full-app call-site scan.
 */
@Suppress("unused")
val adsRegexAdvancePatch = bytecodePatch(
    name = "Ads Regex Advance",
    description = "Low-memory ad SDK killer (only ad packages, no full-app scan).",
    default = false,
) {
    execute {
        val sdkHints = listOf(
            "/ads/", "admob", "applovin", "unity3d/ads", "vungle",
            "ironsource", "facebook/ads", "adcolony", "chartboost",
            "inmobi", "mopub", "mintegral", "mbridge", "pangle",
            "bytedance/sdk/openadsdk", "startapp", "tapjoy",
            "gms/ads", "AdActivity", "AdView", "InterstitialAd",
            "RewardedAd", "AdLoader", "MobileAds",
        )

        val actionHints = listOf(
            "loadad", "showad", "loadinterstitial", "showinterstitial",
            "loadrewarded", "showrewarded", "loadbanner", "showbanner",
            "loadnative", "shownative", "loadappopen", "requestad",
            "fetchad", "isloaded", "isready", "canshow",
        )

        val skipParts = listOf(
            "close", "destroy", "dismiss", "dispose", "release",
            "error", "fail", "throw", "log",
        )

        fun String.isAdClass() =
            sdkHints.any { contains(it, ignoreCase = true) }

        fun String.hasAction() =
            actionHints.any { contains(it, ignoreCase = true) }

        fun String.shouldSkip() =
            skipParts.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            // LOW MEMORY: skip non-ad classes immediately (no mutableClassDef)
            if (!classDef.type.isAdClass()) return@classDefForEach

            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.instructionsOrNull == null) return@forEach
                if (method.name.shouldSkip()) return@forEach
                if (!method.name.hasAction()) return@forEach

                when (method.returnType) {
                    "V" -> method.addInstructions(0, "return-void")
                    "Z" -> method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
