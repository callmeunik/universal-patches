package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

/**
 * Remove All Ads Ultimate
 * Combines safe manifest cleanup + careful bytecode neutralization.
 * Designed to work on most apps with minimal crash risk.
 */
@Suppress("unused")
val removeAllAdsUltimatePatch = bytecodePatch(
    name = "Remove All Ads Ultimate",
    description = "Safe + powerful ad remover. Cleans manifest and carefully disables common ad SDK load/show/isLoaded calls without breaking apps.",
    default = false,
) {
    dependsOn(removeAllAdsUltimateResourcePatch)

    execute {
        val skipNameParts = listOf(
            "close", "destroy", "dismiss", "dispose", "release",
            "hide", "stop", "pause", "cancel", "remove",
            "error", "fail", "throw", "exception", "log",
        )

        val safeLoadShowHints = listOf(
            "loadad", "load_ad", "showad", "show_ad",
            "loadinterstitial", "showinterstitial",
            "loadrewarded", "showrewarded",
            "loadbanner", "showbanner",
            "loadnative", "shownative",
            "loadappopen", "showappopen",
            "requestad", "fetchad", "displayad",
            "isloaded", "isready", "isadavailable", "canshow", "isshowing",
        )

        fun String.shouldSkip(): Boolean =
            skipNameParts.any { contains(it, ignoreCase = true) }

        fun String.isSafeAdAction(): Boolean =
            safeLoadShowHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val className = classDef.type
            val isAdClass = className.isAdRelated()

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // ---- A) Inside known ad SDK classes (safe method names only) ----
                if (isAdClass && !methodName.shouldSkip()) {
                    when {
                        method.returnType == "Z" && methodName.isSafeAdAction() -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }

                        method.returnType == "V" && methodName.isSafeAdAction() -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                // ---- B) Call-sites from app code ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val defining = reference.definingClass
                    val refName = reference.name

                    if (refName.shouldSkip()) return@forEachIndexed

                    val isAdCall =
                        defining.isAdRelated() ||
                            refName.isSafeAdAction() ||
                            refName.contains("loadAd", true) ||
                            refName.contains("showAd", true) ||
                            refName.contains("isLoaded", true) ||
                            refName.contains("isAdLoaded", true)

                    if (!isAdCall) return@forEachIndexed

                    when (reference.returnType) {
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null &&
                                (next.opcode == Opcode.MOVE_RESULT ||
                                    next.opcode == Opcode.MOVE_RESULT_OBJECT)
                            ) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0",
                                )
                            }
                        }

                        "V" -> {
                            // Only nop pure void load/show style calls (safer than rewriting whole method)
                            if (refName.isSafeAdAction()) {
                                method.replaceInstruction(index, "nop")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared ad keyword list (used by both resource + bytecode layers)
 */
private val adKeywords = listOf(
    // Google / AdMob
    "com.google.android.gms.ads",
    "com.google.ads",
    "com.google.android.gms.ads.AdActivity",
    "com.google.android.gms.ads.MobileAds",
    "com.google.android.gms.ads.AdService",
    "com.google.android.gms.ads.identifier",
    "com.google.android.gms.measurement",
    "com.google.firebase.analytics",
    "com.google.android.gms.analytics",
    "com.google.android.gms.permission.AD_ID",

    // Meta / Facebook
    "com.facebook.ads",
    "com.facebook.advertising",
    "AudienceNetwork",

    // Major networks
    "com.unity3d.ads",
    "com.unity3d.services.ads",
    "com.applovin",
    "com.ironsource",
    "com.vungle",
    "com.chartboost",
    "com.adcolony",
    "com.mopub",
    "com.inmobi",
    "com.startapp",
    "com.yandex.mobile.ads",
    "com.bytedance.sdk.openadsdk",
    "com.pangle",
    "com.mintegral",
    "com.mbridge",
    "com.smaato",
    "com.tapjoy",
    "com.my.target",
    "com.hyprmx",
    "com.ogury",
    "com.pubmatic",
    "com.criteo",
    "com.amazon.device.ads",
    "com.flurry",
    "com.adjust",
    "com.appsflyer",
    "com.kochava",
    "com.singular",
    "io.branch",

    // Generic (kept reasonably strict)
    "AdActivity",
    "AdService",
    "AdsActivity",
    "InterstitialAd",
    "RewardedAd",
    "BannerAd",
    "AdMob",
    "AdView",
    "adservice",
)

private fun String.isAdRelated(): Boolean =
    adKeywords.any { contains(it, ignoreCase = true) }

/**
 * Layer 1 – Manifest cleanup (safe)
 */
private val removeAllAdsUltimateResourcePatch = resourcePatch(
    description = "Removes ad-related components, meta-data and permissions from the manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val componentTags = listOf(
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
            )

            // 1) Components
            componentTags.forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    if (el.getAttribute("android:name").isAdRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }

            // 2) meta-data
            val metaNodes = doc.getElementsByTagName("meta-data")
            val metaToRemove = mutableListOf<org.w3c.dom.Node>()
            for (i in 0 until metaNodes.length) {
                val el = metaNodes.item(i) as? Element ?: continue
                val name = el.getAttribute("android:name")
                val value = el.getAttribute("android:value")
                if (name.isAdRelated() || value.isAdRelated()) {
                    metaToRemove.add(el)
                }
            }
            metaToRemove.forEach { it.parentNode?.removeChild(it) }

            // 3) Permissions
            val adPermissions = setOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
                "android.permission.ACCESS_ADSERVICES_TOPICS",
            )

            listOf("uses-permission", "uses-permission-sdk-23").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("android:name")
                    if (name in adPermissions || name.isAdRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }
        }
    }
}
