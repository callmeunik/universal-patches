package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

/**
 * Changes the app display name (android:label) shown under the icon.
 */
@Suppress("unused")
val changeAppNamePatch = resourcePatch(
    name = "Change app name",
    description = "Changes the app name shown on the launcher.",
    default = false,
) {
    val appName by stringOption(
        key = "universalAppName",
        default = "My App",
        title = "App name",
        description = "New name to show under the app icon.",
        required = true,
    )

    execute {
        val newName = appName ?: "My App"

        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
            application?.setAttribute("android:label", newName)

            // Also update MAIN launcher activity label if present
            val activities = doc.getElementsByTagName("activity")
            for (i in 0 until activities.length) {
                val activity = activities.item(i) as? Element ?: continue
                val filters = activity.getElementsByTagName("intent-filter")
                for (j in 0 until filters.length) {
                    val filter = filters.item(j) as? Element ?: continue
                    val children = filter.childNodes
                    var isMain = false
                    var isLauncher = false
                    for (k in 0 until children.length) {
                        val child = children.item(k) as? Element ?: continue
                        val n = child.getAttribute("android:name")
                        if (child.tagName == "action" && n == "android.intent.action.MAIN") isMain = true
                        if (child.tagName == "category" && n == "android.intent.category.LAUNCHER") isLauncher = true
                    }
                    if (isMain && isLauncher) {
                        activity.setAttribute("android:label", newName)
                    }
                }
            }
        }

        // Try strings.xml app_name if exists
        runCatching {
            document("res/values/strings.xml").use { doc ->
                val strings = doc.getElementsByTagName("string")
                for (i in 0 until strings.length) {
                    val el = strings.item(i) as? Element ?: continue
                    val name = el.getAttribute("name")
                    if (name == "app_name" || name == "application_name" || name == "appName") {
                        el.textContent = newName
                    }
                }
            }
        }
    }
}
