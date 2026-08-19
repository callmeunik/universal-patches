package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch

/**
 * Ads Regex Basic — LOW MEMORY
 * Only ad-SDK classes. No full-app invoke scan.
 */
@Suppress("unused")
val adsRegexBasicPatch = bytecodePatch(
    name = "Ads Regex Basic",
    description = "Low-memory basic ad killer (ad packages only).",
    default = false,
) {
    execute {
        val sdkHints = listOf(
            "/ads/", "admob", "gms/ads", "applovin", "unity3d/ads",
            "vungle", "ironsource", "facebook/ads", "AdActivity",
            "AdView", "Interstitial", "Rewarded", "MobileAds",
        )

        val actions = listOf(
            "loadad", "showad", "loadads", "showads",
            "loadinterstitial", "showinterstitial",
            "loadrewarded", "showrewarded",
            "isloaded", "isready", "canshow",
        )

        fun String.isAdClass() =
            sdkHints.any { contains(it, ignoreCase = true) }

        fun String.hasAction() =
            actions.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            if (!classDef.type.isAdClass()) return@classDefForEach

            mutableClassDefBy(classDef).methods.forEach { method ->
                if (method.instructionsOrNull == null) return@forEach
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
