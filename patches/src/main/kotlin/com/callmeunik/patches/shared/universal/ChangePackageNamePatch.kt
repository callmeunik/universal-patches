package com.callmeunik.patches.shared.universal

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

/**
 * Changes the package name in AndroidManifest.xml.
 *
 * WARNING:
 * - Can break login, Play services, permissions, FileProvider, deep links.
 * - Not a full smali/resource rewrite. Use carefully.
 * - Best for sideload / testing, not always for production apps.
 */
@Suppress("unused")
val changePackageNamePatch = resourcePatch(
    name = "Change package name",
    description = "Changes the package name in the manifest. May break some app features.",
    default = false,
) {
    val newPackage by stringOption(
        key = "universalPackageName",
        default = "com.example.renamed",
        title = "New package name",
        description = "Example: com.myname.appclone",
        required = true,
    ) {
        it != null && it.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))
    }

    execute {
        val pkg = newPackage ?: return@execute

        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement
            val oldPackage = manifest.getAttribute("package")

            // 1) Root package
            manifest.setAttribute("package", pkg)

            // 2) Fix relative class names (.MainActivity → full name with NEW package)
            //    Absolute names (com.old...) are left as-is (still point to old code package)
            val componentTags = listOf(
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
            )

            componentTags.forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue

                    // android:name
                    val name = el.getAttribute("android:name")
                    if (name.startsWith(".")) {
                        el.setAttribute("android:name", pkg + name)
                    }

                    // android:targetActivity
                    val target = el.getAttribute("android:targetActivity")
                    if (target.startsWith(".")) {
                        el.setAttribute("android:targetActivity", pkg + target)
                    }

                    // android:authorities for providers
                    val authorities = el.getAttribute("android:authorities")
                    if (authorities.isNotEmpty() && oldPackage.isNotEmpty()) {
                        el.setAttribute(
                            "android:authorities",
                            authorities.replace(oldPackage, pkg),
                        )
                    }

                    // android:process
                    val process = el.getAttribute("android:process")
                    if (process.startsWith(":")) {
                        // private process, keep
                    } else if (process.isNotEmpty() && oldPackage.isNotEmpty() && process.contains(oldPackage)) {
                        el.setAttribute("android:process", process.replace(oldPackage, pkg))
                    }
                }
            }

            // 3) permission / uses-permission custom names with old package
            listOf("permission", "uses-permission", "uses-permission-sdk-23").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val n = el.getAttribute("android:name")
                    if (oldPackage.isNotEmpty() && n.startsWith(oldPackage)) {
                        el.setAttribute("android:name", n.replace(oldPackage, pkg))
                    }
                }
            }
        }
    }
}
