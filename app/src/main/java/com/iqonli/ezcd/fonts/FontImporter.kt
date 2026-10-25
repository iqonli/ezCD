// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

data class FontImportIssue(val name: String, val reason: String)
data class FontImportReport(val added: Int, val duplicates: Int, val issues: List<FontImportIssue>, val failures: Int = issues.size)

/** Archive paths are labels only; no archive entry is ever extracted to its supplied path. */
class FontImporter(context: Context) {
    private val context = context.applicationContext
    private val store = FontStore(this.context)

    fun import(uris: List<Uri>, progress: (Int) -> Unit = {}): FontImportReport {
        FontCache.clean(context)
        require(uris.size in 1..MAX_CANDIDATES) { "Too many selected files" }
        var added = 0
        var duplicates = 0
        var totalBytes = 0L
        var candidates = 0
        var entries = 0
        var failures = 0
        progress(0)
        val issues = mutableListOf<FontImportIssue>()
        fun issue(name: String, error: Exception) {
            failures++
            if (issues.size < 40) issues += FontImportIssue(name.take(256), error.message?.take(180) ?: error.javaClass.simpleName)
        }
        fun read(input: InputStream, output: java.io.OutputStream?, limit: Long) {
            val buffer = ByteArray(64 * 1024)
            var bytes = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                bytes += n; totalBytes += n
                require(bytes <= limit && totalBytes <= MAX_BATCH_BYTES) { "Font import size limit exceeded" }
                output?.write(buffer, 0, n)
            }
        }
        fun install(input: InputStream, name: String, source: FontSource) {
            require(++candidates <= MAX_CANDIDATES) { "Too many fonts in this import" }
            FontCache.withFile(context) { temporary ->
                temporary.outputStream().use { read(input, it, MAX_FILE_BYTES) }
                try {
                    val (new, duplicate) = store.install(temporary, name, source)
                    added += new; duplicates += duplicate
                } catch (error: Exception) { issue(name, error) }
            }
        }
        uris.forEachIndexed { index, uri ->
            val name = displayName(uri)
            try {
                val mode = store.storage().mode
                check(mode != FontStorageMode.UNSELECTED) { context.getString(com.iqonli.ezcd.R.string.ez_font_storage_required) }
                val source = if (mode == FontStorageMode.REFERENCE) store.reference(uri) else FontSource(uri.toString())
                checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open file" }.use { raw ->
                    val size = sourceSize(uri)
                    var consumed = 0L
                    var classified=false
                    var archiveInput=false
                    fun advanced(n: Int) {
                        if(n>0) consumed+=n
                        if (classified && !archiveInput && size > 0) {
                            val fraction = (consumed.toDouble() / size).coerceIn(0.0, 1.0) * .95
                            progress(((index + fraction) * 100 / uris.size).toInt().coerceAtMost(99))
                        }
                    }
                    val counting = object : InputStream() {
                        override fun read(): Int = raw.read().also { if (it >= 0) advanced(1) }
                        override fun read(b: ByteArray, off: Int, len: Int): Int = raw.read(b, off, len).also(::advanced)
                    }
                    val input = BufferedInputStream(counting)
                    input.mark(4)
                    val signature = ByteArray(4)
                    var n=0
                    while(n<signature.size) { val count=input.read(signature,n,signature.size-n);if(count<0) break;n+=count }
                    input.reset()
                    val zip = name.endsWith(".zip", true) || n == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()
                    archiveInput=zip;classified=true
                    if(!zip) advanced(0)
                    if (!zip) install(input, name, source)
                    else {
                        val before = candidates
                        FontZip.open(context,uri,{ bytes ->
                            if(size>0) progress(((index+(bytes.toDouble()/size).coerceIn(0.0,1.0)*.35)*100/uris.size).toInt().coerceAtMost(99))
                        },input) { archive ->
                            archive.entries.forEachIndexed { entryIndex,entry ->
                                require(++entries <= 10000) { "Too many archive entries" }
                                val path = entry.name.replace('\\', '/')
                                val leaf = path.substringAfterLast('/')
                                if (!entry.isDirectory && !path.startsWith("__MACOSX/") && !leaf.startsWith("._") && supported(leaf)) {
                                    archive.read(entryIndex) { install(it, leaf, source.copy(zipEntry = entry.name, zipIndex = entryIndex)) }
                                }
                                progress(((index+.35+.6*(entryIndex+1)/archive.entries.size)*100/uris.size).toInt().coerceAtMost(99))
                            }
                        }
                        require(candidates > before) { "No supported fonts in ZIP (TTF, OTF, TTC, OTC)" }
                    }
                }
            } catch (error: Exception) { issue(name, error) }
            progress(((index + 1) * 100 / uris.size).coerceAtMost(99))
            // An oversized archive ends the whole batch, rather than restarting its byte budget.
            if (totalBytes > MAX_BATCH_BYTES || candidates > MAX_CANDIDATES || entries > 10000) return FontImportReport(added, duplicates, issues, failures)
        }
        progress(100)
        return FontImportReport(added, duplicates, issues, failures)
    }

    private fun sourceSize(uri: Uri): Long = if (uri.scheme == "file") File(uri.path.orEmpty()).length() else runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), android.os.Bundle.EMPTY, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), android.os.Bundle.EMPTY, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"

    companion object {
        const val MAX_CANDIDATES = 2048
        const val MAX_FILE_BYTES = 128L * 1024 * 1024
        const val MAX_BATCH_BYTES = 512L * 1024 * 1024
        fun supported(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("ttf", "otf", "ttc", "otc")
    }
}
