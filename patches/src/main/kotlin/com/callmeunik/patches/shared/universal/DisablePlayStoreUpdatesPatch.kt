package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Disables Play Store auto-update related components from the app manifest.
 * Helps stop the app from being forced/updated via Play Store mechanisms
 * that are declared inside the APK itself.
 */
@Suppress("unused")
val disablePlayStoreUpdatesPatch = resourcePatch(
    name = "Disable Play Store updates",
    description = "Removes Play Store update / installer related components and permissions from the manifest.",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val updateKeywords = listOf(
                "com.google.android.play.core",
                "com.google.android.play.core.appupdate",
                "com.google.android.play.core.install",
                "AppUpdateActivity",
                "AppUpdateService",
                "AppUpdateReceiver",
                "PlayCoreDialogWrapperActivity",
                "com.google.android.finsky",
                "com.android.vending",
                "com.google.android.gms.update",
                "assetpacks",
                "AssetPackExtractionService",
                "AssetPackService",
                "ReviewActivity",
                "ReviewService",
                "inappupdate",
                "InAppUpdate",
            )

            fun String.isUpdateRelated(): Boolean =
                updateKeywords.any { contains(it, ignoreCase = true) }

            // 1) Remove update-related components
            val tags = listOf(
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
            )

            tags.forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()

                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("android:name")
                    if (name.isUpdateRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }

            // 2) Remove related meta-data
            val metaNodes = doc.getElementsByTagName("meta-data")
            val metaToRemove = mutableListOf<org.w3c.dom.Node>()
            for (i in 0 until metaNodes.length) {
                val el = metaNodes.item(i) as? Element ?: continue
                val name = el.getAttribute("android:name")
                val value = el.getAttribute("android:value")
                if (name.isUpdateRelated() || value.isUpdateRelated()) {
                    metaToRemove.add(el)
                }
            }
            metaToRemove.forEach { it.parentNode?.removeChild(it) }

            // 3) Remove Play Core / update permissions if present
            val updatePermissions = setOf(
                "com.google.android.play.core.permission.INSTALL",
                "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
            )

            val permissionTags = listOf("uses-permission", "uses-permission-sdk-23")
            permissionTags.forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("android:name")
                    if (name in updatePermissions || name.isUpdateRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }
        }
    }
}
