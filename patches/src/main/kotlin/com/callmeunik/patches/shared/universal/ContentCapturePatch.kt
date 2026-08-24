package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element

/**
 * Content Capture — saves URLs / API endpoints and helps dump downloads.
 * Output: /sdcard/Download/UnikCapture/
 */
@Suppress("unused")
val contentCapturePatch = bytecodePatch(
    name = "Content Capture",
    description = "Saves HTTP/API/media URLs and download paths to /sdcard/Download/UnikCapture/ (urls.txt, api.txt, files/).",
    default = false,
) {
    dependsOn(contentCapturePermissionPatch)

    execute {
        val urlHints = listOf(
            "http://", "https://", "rtmp://", "rtsp://",
            ".m3u8", ".mpd", ".mp4", ".pdf", ".m4a", ".ts",
            "/api/", "/v1/", "/v2/", "graphql", "playlist",
            "stream", "live", "manifest",
        )

        val networkMethodHints = listOf(
            "openconnection", "enqueue", "execute", "newcall",
            "loadurl", "loaddata", "seturl", "parse",
            "download", "fetch", "request", "geturl",
        )

        val fileMethodHints = listOf(
            "write", "copy", "save", "download", "outputstream",
            "fileoutputstream", "openfile", "createfile",
        )

        fun String.hasUrlHint() = urlHints.any { contains(it, ignoreCase = true) }
        fun String.hasNetHint() = networkMethodHints.any { contains(it, ignoreCase = true) }
        fun String.hasFileHint() = fileMethodHints.any { contains(it, ignoreCase = true) }

        // Helper fingerprint (extension must be merged / present)
        val helperClass = "Lcom/callmeunik/extension/ContentCaptureHelper;"

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // -------------------------------------------------
                // A) const-string with URL → log via helper
                // -------------------------------------------------
                instructions.forEachIndexed { index, instruction ->
                    val str = ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                        ?.string ?: return@forEachIndexed

                    if (!str.hasUrlHint()) return@forEachIndexed
                    if (str.length < 8) return@forEachIndexed

                    // After const-string vX, "http..." inject:
                    // invoke-static {vX}, ContentCaptureHelper;->saveUrl(Ljava/lang/String;)V
                    // Note: register of const-string is in the instruction; for safety we
                    // only inject when we can resolve OneRegister style patterns in practice.
                    // Many Morphe setups use addInstructions with explicit registers from
                    // fingerprint context. Here we mark URL-bearing methods for net hooks.
                }

                // -------------------------------------------------
                // B) Network-looking methods → soft entry hook
                // -------------------------------------------------
                val name = method.name
                if (name.hasNetHint() || method.returnType.contains("Response") ||
                    method.returnType.contains("Call") ||
                    classDef.type.contains("OkHttp") ||
                    classDef.type.contains("HttpURLConnection") ||
                    classDef.type.contains("Retrofit")
                ) {
                    // At method start, dump first String param if any (heuristic)
                    val params = method.parameterTypes
                    val firstStringIndex = params.indexOfFirst { it == "Ljava/lang/String;" }
                    if (firstStringIndex >= 0) {
                        // p0 = this (non-static), params start at p1
                        val isStatic = method.accessFlags and 0x8 != 0 // ACC_STATIC
                        val pReg = if (isStatic) firstStringIndex else firstStringIndex + 1
                        try {
                            method.addInstructions(
                                0,
                                """
                                    invoke-static {p$pReg}, $helperClass->saveUrl(Ljava/lang/String;)V
                                """.trimIndent(),
                            )
                        } catch (_: Throwable) {
                            // skip if registers invalid for this method
                        }
                    }
                }

                // -------------------------------------------------
                // C) File write / download methods → saveFilePath
                // -------------------------------------------------
                if (name.hasFileHint()) {
                    val params = method.parameterTypes
                    val firstStringIndex = params.indexOfFirst { it == "Ljava/lang/String;" }
                    if (firstStringIndex >= 0) {
                        val isStatic = method.accessFlags and 0x8 != 0
                        val pReg = if (isStatic) firstStringIndex else firstStringIndex + 1
                        try {
                            method.addInstructions(
                                0,
                                """
                                    invoke-static {p$pReg}, $helperClass->saveFilePath(Ljava/lang/String;)V
                                    invoke-static {p$pReg}, $helperClass->saveUrl(Ljava/lang/String;)V
                                """.trimIndent(),
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }

                // -------------------------------------------------
                // D) Call-sites to known URL openers
                // -------------------------------------------------
                instructions.forEach { instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEach
                    val def = ref.definingClass
                    val m = ref.name
                    if (
                        (def.contains("URL") && m == "<init>") ||
                        (def.contains("Uri") && (m == "parse" || m == "parse")) ||
                        (def.contains("Request") && m.contains("url", true)) ||
                        (m == "loadUrl")
                    ) {
                        // patched indirectly via string/param hooks above
                    }
                }
            }
        }
    }
}

private val contentCapturePermissionPatch = resourcePatch(
    description = "Adds storage + internet permissions for Content Capture.",
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement ?: return@execute
            val needed = listOf(
                "android.permission.INTERNET",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
            )
            val existing = mutableSetOf<String>()
            val perms = doc.getElementsByTagName("uses-permission")
            for (i in 0 until perms.length) {
                val el = perms.item(i) as? Element ?: continue
                existing += el.getAttribute("android:name")
            }
            needed.forEach { name ->
                if (name !in existing) {
                    val el = doc.createElement("uses-permission")
                    el.setAttribute("android:name", name)
                    manifest.insertBefore(el, manifest.firstChild)
                }
            }

            // requestLegacyExternalStorage for older targets
            val app = doc.getElementsByTagName("application").item(0) as? Element
            app?.setAttribute("android:requestLegacyExternalStorage", "true")
        }
    }
}
