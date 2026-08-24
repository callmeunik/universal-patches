package com.callmeunik.extension

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Saves files + API/media URLs to:
 * /sdcard/Download/UnikCapture/
 *   ├── files/     (pdf, images, downloads)
 *   ├── urls.txt   (all http/https URLs)
 *   └── api.txt    (api-looking endpoints)
 */
object ContentCaptureHelper {

    private const val TAG = "UnikCapture"
    private val io = Executors.newSingleThreadExecutor()

    private val rootDir: File by lazy {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(base, "UnikCapture").also { it.mkdirs() }
    }

    private val filesDir: File by lazy {
        File(rootDir, "files").also { it.mkdirs() }
    }

    private val urlLog: File by lazy { File(rootDir, "urls.txt") }
    private val apiLog: File by lazy { File(rootDir, "api.txt") }

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val nameFmt = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    // -------------------------------------------------------------------------
    // Public API (called from bytecode hooks)
    // -------------------------------------------------------------------------

    /** Log any URL (video, live, api, cdn, etc.) */
    @JvmStatic
    fun saveUrl(url: String?) {
        if (url.isNullOrBlank()) return
        if (!url.startsWith("http", ignoreCase = true) &&
            !url.startsWith("//")
        ) return

        io.execute {
            try {
                appendLine(urlLog, url.trim())
                if (looksLikeApi(url)) {
                    appendLine(apiLog, url.trim())
                }
            } catch (t: Throwable) {
                Log.w(TAG, "saveUrl failed", t)
            }
        }
    }

    /** Save raw bytes (e.g. PDF / download body) */
    @JvmStatic
    fun saveBytes(data: ByteArray?, suggestedName: String?) {
        if (data == null || data.isEmpty()) return
        io.execute {
            try {
                val name = safeName(suggestedName) ?: "file_${nameFmt.format(Date())}.bin"
                val out = uniqueFile(filesDir, name)
                FileOutputStream(out).use { it.write(data) }
                Log.i(TAG, "Saved bytes → ${out.absolutePath}")
                appendLine(urlLog, "FILE:${out.absolutePath}")
            } catch (t: Throwable) {
                Log.w(TAG, "saveBytes failed", t)
            }
        }
    }

    /** Copy an existing file path into UnikCapture/files */
    @JvmStatic
    fun saveFilePath(path: String?) {
        if (path.isNullOrBlank()) return
        io.execute {
            try {
                val src = File(path)
                if (!src.exists() || !src.isFile) return@execute
                val dest = uniqueFile(filesDir, src.name)
                src.copyTo(dest, overwrite = false)
                Log.i(TAG, "Copied → ${dest.absolutePath}")
                appendLine(urlLog, "FILE:${dest.absolutePath} SRC:$path")
            } catch (t: Throwable) {
                Log.w(TAG, "saveFilePath failed", t)
            }
        }
    }

    /** Download URL to files/ (best-effort, background) */
    @JvmStatic
    fun downloadUrl(url: String?, suggestedName: String?) {
        if (url.isNullOrBlank()) return
        saveUrl(url)
        io.execute {
            try {
                val conn = URL(url).openConnection().apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val name = suggestedName
                    ?: url.substringAfterLast('/')
                        .substringBefore('?')
                        .ifBlank { "dl_${nameFmt.format(Date())}" }

                val out = uniqueFile(filesDir, safeName(name) ?: "download.bin")
                conn.getInputStream().use { input ->
                    FileOutputStream(out).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Downloaded → ${out.absolutePath}")
                appendLine(urlLog, "DL:${out.absolutePath} URL:$url")
            } catch (t: Throwable) {
                Log.w(TAG, "downloadUrl failed: $url", t)
            }
        }
    }

    /** Convenience for InputStream (caller must not close early if shared) */
    @JvmStatic
    fun saveStream(stream: InputStream?, suggestedName: String?) {
        if (stream == null) return
        io.execute {
            try {
                val bytes = stream.readBytes()
                saveBytes(bytes, suggestedName)
            } catch (t: Throwable) {
                Log.w(TAG, "saveStream failed", t)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun looksLikeApi(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("/api") ||
            u.contains("/v1/") ||
            u.contains("/v2/") ||
            u.contains("/v3/") ||
            u.contains("graphql") ||
            u.contains("m3u8") ||
            u.contains(".mpd") ||
            u.contains("playlist") ||
            u.contains("manifest") ||
            u.contains("stream") ||
            u.contains("live") ||
            u.contains("token=") ||
            u.contains("auth")
    }

    private fun appendLine(file: File, line: String) {
        val stamp = timeFmt.format(Date())
        file.parentFile?.mkdirs()
        file.appendText("[$stamp] $line\n")
    }

    private fun safeName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "${base}_\( i" else " \){base}_$i.$ext")
            i++
        }
        return f
    }
}
