package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Universal ads cleaner (safe mode).
 * Removes common ad SDK components, permissions and meta-data from the manifest.
 * Does not touch app code deeply, so lower chance of crashing.
 */
@Suppress("unused")
val removeAllAdsPatch = resourcePatch(
    name = "Remove all ads",
    description = "Removes common ad SDK activities, services, receivers, providers, permissions and meta-data from the manifest.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val adKeywords = listOf(
                // Google
                "com.google.android.gms.ads",
                "com.google.ads",
                "com.google.android.gms.ads.AdActivity",
                "com.google.android.gms.ads.MobileAds",
                "com.google.android.gms.ads.AdService",
                "com.google.android.gms.ads.identifier",
                "com.google.android.gms.measurement",
                "com.google.firebase.analytics",
                "com.google.android.gms.analytics",

                // Facebook / Meta
                "com.facebook.ads",
                "com.facebook.advertising",
                "AudienceNetwork",

                // Unity / AppLovin / IronSource / etc.
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
                "com.verve",
                "com.criteo",
                "com.amazon.device.ads",
                "com.flurry",
                "com.adjust",
                "com.appsflyer",
                "com.kochava",
                "com.singular",
                "io.branch",
                "com.tencent.mm.opensdk", // sometimes used with ads

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
            )

            fun String.isAdRelated(): Boolean =
                adKeywords.any { contains(it, ignoreCase = true) }

            // 1) Remove ad components
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
                    val name = el.getAttribute("android:name")
                    if (name.isAdRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }

            // 2) Remove ad-related meta-data
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

            // 3) Remove ad / tracking permissions
            val adPermissions = setOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
                "android.permission.ACCESS_ADSERVICES_TOPICS",
                "com.google.android.gms.permission.AD_ID",
            )

            val permissionTags = listOf("uses-permission", "uses-permission-sdk-23")
            permissionTags.forEach { tag ->
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

            // 4) Remove intent-filters clearly tied to ads
            val filters = doc.getElementsByTagName("intent-filter")
            val filtersToRemove = mutableListOf<org.w3c.dom.Node>()
            for (i in 0 until filters.length) {
                val filter = filters.item(i) as? Element ?: continue
                val children = filter.childNodes
                var adFilter = false
                for (j in 0 until children.length) {
                    val child = children.item(j) as? Element ?: continue
                    val n = child.getAttribute("android:name")
                    if (n.isAdRelated()) {
                        adFilter = true
                        break
                    }
                }
                if (adFilter) filtersToRemove.add(filter)
            }
            filtersToRemove.forEach { it.parentNode?.removeChild(it) }
        }
    }
}
