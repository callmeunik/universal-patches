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

    // Other big networks
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

    // Generic
    "AdActivity",
    "AdService",
    "AdsActivity",
    "Interstitial",
    "RewardedAd",
    "BannerAd",
    "AdMob",
    "advertising",
    "adservice",
    "loadAd",
    "showAd",
    "isLoaded",
    "adview",
    "AdView",
    "InterstitialAd",
    "RewardedVideo",
)

private fun String.isAdRelated(): Boolean =
    adKeywords.any { contains(it, ignoreCase = true) }

/**
 * Layer 1: Manifest cleanup
 */
private val removeAllAdsResourcePatch = resourcePatch(
    description = "Removes ad-related manifest components and permissions.",
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

            // meta-data
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

            // permissions
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

/**
 * Layer 2: Bytecode — kill ad load/show/isLoaded calls
 */
@Suppress("unused")
val removeAllAdsPowerfulPatch = bytecodePatch(
    name = "Remove all ads (powerful)",
    description = "Removes ad components from manifest and disables common ad SDK load/show/isLoaded calls.",
    default = false,
) {
    dependsOn(removeAllAdsResourcePatch)

    execute {
        classDefForEach { classDef ->
            val isAdClass = classDef.type.isAdRelated()

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // A) Inside ad SDK classes → neutralize key methods
                if (isAdClass) {
                    when {
                        method.returnType == "Z" &&
                            (methodName.contains("isLoaded", true) ||
                                methodName.contains("isReady", true) ||
                                methodName.contains("isAdAvailable", true) ||
                                methodName.contains("canShow", true) ||
                                methodName.contains("isShowing", true)) -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }

                        method.returnType == "V" &&
                            (methodName.contains("load", true) ||
                                methodName.contains("show", true) ||
                                methodName.contains("display", true) ||
                                methodName.contains("request", true) ||
                                methodName.contains("fetch", true) ||
                                methodName.contains("start", true) ||
                                methodName.contains("init", true) ||
                                methodName.contains("initialize", true)) -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                // B) Call-sites from app code → force safe results
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val defining = reference.definingClass
                    val refName = reference.name

                    val isAdCall =
                        defining.isAdRelated() ||
                            refName.contains("loadAd", true) ||
                            refName.contains("showAd", true) ||
                            refName.contains("isLoaded", true) ||
                            refName.contains("isAdLoaded", true) ||
                            refName.contains("showInterstitial", true) ||
                            refName.contains("showRewarded", true) ||
                            refName.contains("loadInterstitial", true) ||
                            refName.contains("loadRewarded", true) ||
                            refName.contains("loadBanner", true)

                    if (!isAdCall) return@forEachIndexed

                    val next = instructions.getOrNull(index + 1)

                    when (reference.returnType) {
                        "Z" -> {
                            val move = next as? OneRegisterInstruction
                            if (move != null &&
                                (move.opcode == Opcode.MOVE_RESULT ||
                                    move.opcode == Opcode.MOVE_RESULT_OBJECT)
                            ) {
                                // isLoaded / canShow → false
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${move.registerA}, 0x0",
                                )
                            }
                        }

                        "V" -> {
                            // load/show void calls — leave invoke, but SDK methods already neutered
                        }
                    }
                }
            }
        }
    }
}
