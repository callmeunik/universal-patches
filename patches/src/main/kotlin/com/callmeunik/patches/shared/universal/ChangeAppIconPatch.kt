package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

/**
 * Change App Icon
 * Updates android:icon / android:roundIcon in the manifest.
 */
@Suppress("unused")
val changeAppIconPatch = resourcePatch(
    name = "Change App Icon",
    description = "Changes the launcher icon resource name in the manifest.",
    default = false,
) {
    val iconName by stringOption(
        key = "iconName",
        default = "ic_launcher",
        title = "Icon resource name",
        description = "Base name of the launcher icon (usually ic_launcher)",
    )

    execute {
        val name = iconName ?: "ic_launcher"

        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
                ?: return@execute

            application.setAttribute("android:icon", "@mipmap/$name")
            application.setAttribute("android:roundIcon", "@mipmap/${name}_round")
        }
    }
}
