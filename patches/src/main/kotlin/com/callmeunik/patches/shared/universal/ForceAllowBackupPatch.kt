package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Force Allow Backup — sets android:allowBackup="true" in the manifest.
 */
@Suppress("unused")
val forceAllowBackupPatch = resourcePatch(
    name = "Force Allow Backup",
    description = "Forces android:allowBackup=\"true\" so the app can be backed up.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as? Element
                ?: return@execute

            application.setAttribute("android:allowBackup", "true")

            // Also remove any backup restrictions if present
            if (application.hasAttribute("android:fullBackupContent")) {
                application.removeAttribute("android:fullBackupContent")
            }
            if (application.hasAttribute("android:dataExtractionRules")) {
                // keep the attribute but we already forced allowBackup
            }
        }
    }
}
