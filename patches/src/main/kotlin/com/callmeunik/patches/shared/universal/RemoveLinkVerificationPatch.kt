package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Removes android:autoVerify from intent-filters.
 * Fixes "Open with" / link verification issues on some devices.
 */
@Suppress("unused")
val removeLinkVerificationPatch = resourcePatch(
    name = "Remove link verification",
    description = "Removes autoVerify from intent-filters so app links can be opened manually.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val intentFilters = document.getElementsByTagName("intent-filter")
            for (i in 0 until intentFilters.length) {
                val element = intentFilters.item(i) as? Element ?: continue
                if (element.hasAttribute("android:autoVerify")) {
                    element.removeAttribute("android:autoVerify")
                }
            }
        }
    }
}
