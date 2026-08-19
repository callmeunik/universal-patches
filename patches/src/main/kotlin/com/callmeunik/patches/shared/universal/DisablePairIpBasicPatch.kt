package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Disable PairIP license check (Basic)
 * Manifest-only — removes PairIP license components.
 * No bytecode injection → APK size stays almost the same.
 */
@Suppress("unused")
val disablePairIpBasicPatch = resourcePatch(
    name = "Disable PairIP license check (Basic)",
    description = "Removes PairIP license activity, provider, service, receiver and meta-data from the manifest. Safe and size-friendly.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val keywords = listOf(
                "pairip",
                "com.pairip",
                "licensecheck",
                "LicenseActivity",
                "LicenseClient",
                "LicenseContentProvider",
            )

            fun String.isPairIp(): Boolean =
                keywords.any { contains(it, ignoreCase = true) }

            listOf(
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
                "meta-data",
            ).forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()

                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("android:name")
                    val value = el.getAttribute("android:value")
                    val authorities = el.getAttribute("android:authorities")

                    if (name.isPairIp() || value.isPairIp() || authorities.isPairIp()) {
                        toRemove.add(el)
                    }
                }

                toRemove.forEach { it.parentNode?.removeChild(it) }
            }
        }
    }
}
