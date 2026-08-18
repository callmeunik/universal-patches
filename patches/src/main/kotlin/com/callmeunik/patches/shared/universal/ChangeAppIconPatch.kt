package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.option.stringOption
import java.io.File

/**
 * Change App Icon
 * Replaces the main launcher icon with a custom one (user must provide the icon file).
 * Note: This is a basic resource patch. For full icon replacement place your ic_launcher*.png
 * in the correct density folders before building, or use an external tool + this patch as helper.
 */
@Suppress("unused")
val changeAppIconPatch = resourcePatch(
    name = "Change App Icon",
    description = "Helps change the app launcher icon. Provide new icon resources or use with external icon tools.",
    default = false,
) {
    val iconName by stringOption(
        key = "iconName",
        default = "ic_launcher",
        title = "Icon resource name",
        description = "Base name of the launcher icon (usually ic_launcher)",
    )

    execute {
        // This patch mainly documents + ensures the manifest points to a clean icon name.
        // Full binary PNG replacement is better done externally or with a more advanced resource tool.
        // Here we just make sure the android:icon / android:roundIcon attributes are consistent.

        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? org.w3c.dom.Element
                ?: return@execute

            // Force both icon attributes to the same clean name
            application.setAttribute("android:icon", "@mipmap/$iconName")
            application.setAttribute("android:roundIcon", "@mipmap/${iconName}_round")
        }
    }
}
