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

private val ultimateAdKeywords = listOf(
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
    "com.facebook.ads",
    "com.facebook.advertising",
    "AudienceNetwork",
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

private fun String.isUltimateAdRelated(): Boolean =
    ultimateAdKeywords.any { contains(it, ignoreCase = true) }

private val removeAllAdsUltimateResourcePatch = resourcePatch(
    description = "Removes ad-related components, meta-data and permissions from the manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            listOf("activity", "activity-alias", "service", "receiver", "provider").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    if (el.getAttribute("android:name").isUltimateAdRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }

            val metaNodes = doc.getElementsByTagName("meta-data")
            val metaToRemove = mutableListOf<org.w3c.dom.Node>()
            for (i in 0 until metaNodes.length) {
                val el = metaNodes.item(i) as? Element ?: continue
                val name = el.getAttribute("android:name")
                val value = el.getAttribute("android:value")
                if (name.isUltimateAdRelated() || value.isUltimateAdRelated()) {
                    metaToRemove.add(el)
                }
            }
            metaToRemove.forEach { it.parentNode?.removeChild(it) }

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
                    if (name in adPermissions || name.isUltimateAdRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }
        }
    }
}

@Suppress("unused")
val removeAllAdsUltimatePatch = bytecodePatch(
    name = "Ads Remove Ultimate",
    description = "Safe + powerful ad remover. Cleans manifest and carefully disables common ad SDK load/show/isLoaded calls.",
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

        fun String.shouldSkip() = skipNameParts.any { contains(it, ignoreCase = true) }
        fun String.isSafeAdAction() = safeLoadShowHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val isAdClass = classDef.type.isUltimateAdRelated()

            mutableClassDefBy(classDef).methods.forEach { method ->
                // FIX 1: never touch null implementation
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                // A) Inside ad SDK classes → early return
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

                // B) Call-sites: only boolean force — NO nop
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val defining = reference.definingClass
                    val refName = reference.name
                    if (refName.shouldSkip()) return@forEachIndexed

                    val isAdCall =
                        defining.isUltimateAdRelated() ||
                            refName.isSafeAdAction() ||
                            refName.contains("loadAd", true) ||
                            refName.contains("showAd", true) ||
                            refName.contains("isLoaded", true) ||
                            refName.contains("isAdLoaded", true)

                    if (!isAdCall) return@forEachIndexed

                    if (reference.returnType == "Z") {
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
                    // Void invokes: do NOT use replaceInstruction(..., "nop")
                }
            }
        }
    }
}
