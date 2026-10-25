// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import com.iqonli.ezcd.R
import com.iqonli.ezcd.ui.DirectoryAccess
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/** All directory writes are confined to a new, app-owned child of the selected directory. */
internal class FontFiles(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val marker = ".ezcd-fonts-owner"

    fun reference(uri: Uri): FontSource {
        if (uri.scheme == "file") return FontSource(uri.toString())
        if (durable(uri)) return FontSource(uri.toString())
        val physical = DirectoryAccess.physicalPath(uri)
        if (physical != null && DirectoryAccess.full(context) && physical.isFile && physical.canRead()) return FontSource(Uri.fromFile(physical).toString())
        error(context.getString(R.string.ez_font_reference_permission))
    }

    private fun durable(uri: Uri): Boolean = resolver.persistedUriPermissions.any { grant ->
        grant.isReadPermission && (grant.uri == uri || runCatching {
            DocumentsContract.isTreeUri(grant.uri) && grant.uri.authority == uri.authority &&
                DocumentsContract.getDocumentId(uri).let { id ->
                    val parent = DocumentsContract.getTreeDocumentId(grant.uri)
                    id == parent || id.startsWith(parent.trimEnd('/') + "/")
                }
        }.getOrDefault(false))
    }

    fun createRoot(mode: FontStorageMode, parent: String?): String {
        val name = "ezCD-fonts-" + UUID.randomUUID().toString().take(8)
        val root = if (mode == FontStorageMode.INTERNAL) {
            val file = File(context.filesDir, "font-files/$name")
            check(file.mkdirs()); Uri.fromFile(file)
        } else {
            val p = Uri.parse(checkNotNull(parent))
            if (p.scheme == "file") {
                val file = File(checkNotNull(p.path), name); check(file.mkdir()); Uri.fromFile(file)
            } else {
                val document = DocumentsContract.buildDocumentUriUsingTree(p, DocumentsContract.getTreeDocumentId(p))
                checkNotNull(DocumentsContract.createDocument(resolver, document, DocumentsContract.Document.MIME_TYPE_DIR, name))
            }
        }
        try {
            val mark = create(root.toString(), marker, "text/plain")
            checkNotNull(resolver.openOutputStream(Uri.parse(mark), "wt")).use { it.write("ezCD font storage v1".toByteArray()) }
        } catch (error: Exception) { runCatching { delete(root.toString()) }; throw error }
        return root.toString()
    }

    fun copy(source: FontSource, root: String, font: ImportedFont): String {
        val original = font.fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").substringBeforeLast('.').take(56).ifBlank { "font" }
        val extension = font.fileName.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf", "ttc", "otc") } ?: "ttf"
        val uri = create(root, "$original-${font.blob.take(12)}.$extension", "application/octet-stream")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            checkNotNull(resolver.openOutputStream(Uri.parse(uri), "wt")).use { out ->
                withInput(source) { input -> transfer(input) { b, n -> out.write(b, 0, n); hash.update(b, 0, n) } }
            }
            check(hex(hash.digest()) == font.blob) { context.getString(R.string.ez_font_source_changed) }
        } catch (error: Exception) { runCatching { delete(uri) }; throw error }
        return uri
    }

    fun typeface(source: FontSource, index: Int, variations: String? = null): Typeface {
        fun Typeface.Builder.configured() = setTtcIndex(index).apply { if(variations!=null) setFontVariationSettings(variations) }
        val uri = Uri.parse(source.uri)
        if (source.zipEntry == null) {
            if (uri.scheme == "file") {
                val file = File(checkNotNull(uri.path)); check(file.isFile && file.canRead())
                return checkNotNull(Typeface.Builder(file).configured().build())
            }
            // Direct descriptors keep referenced content outside the app; providers may return pipes.
            val direct = runCatching {
                checkNotNull(resolver.openFileDescriptor(uri, "r")).use { fd -> Typeface.Builder(fd.fileDescriptor).configured().build() }
            }.getOrNull()
            if (direct != null) return direct
        }
        return FontCache.withFile(context) { temporary ->
            temporary.outputStream().use { out -> withInput(source) { input -> transfer(input) { b, n -> out.write(b, 0, n) } } }
            checkNotNull(Typeface.Builder(temporary).configured().build())
        }
    }

    fun <T> withInput(source: FontSource, consume: (InputStream) -> T): T {
        val uri=Uri.parse(source.uri)
        if(source.zipEntry!=null) return FontZip.open(context,uri) { archive ->
            val index=if(source.zipIndex>=0) source.zipIndex else archive.entries.indexOfFirst { it.name==source.zipEntry }
            check(archive.entries.getOrNull(index)?.name==source.zipEntry) { context.getString(R.string.ez_font_source_changed) }
            archive.read(index,consume)
        }
        return checkNotNull(resolver.openInputStream(uri)).use(consume)
    }

    private fun transfer(input: InputStream, write: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(64 * 1024); var bytes = 0L
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            bytes += n; require(bytes <= FontImporter.MAX_FILE_BYTES) { "Font size limit exceeded" }; write(buffer, n)
        }
    }

    private fun create(root: String, name: String, mime: String): String {
        val uri = Uri.parse(root)
        if (uri.scheme == "file") {
            val folder = File(checkNotNull(uri.path)); require(folder.isDirectory)
            var file = File(folder, name)
            if (file.exists()) file = File(folder, UUID.randomUUID().toString().take(8) + "-" + name)
            check(file.createNewFile()); return Uri.fromFile(file).toString()
        }
        return checkNotNull(DocumentsContract.createDocument(resolver, uri, mime, name)).toString()
    }

    fun exists(raw: String): Boolean {
        val uri = Uri.parse(raw)
        if (uri.scheme == "file") return File(checkNotNull(uri.path)).exists()
        return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), android.os.Bundle.EMPTY, null)?.use { it.moveToFirst() }
            ?: error("Cannot inspect font document")
    }

    fun delete(raw: String) {
        val uri = Uri.parse(raw)
        if (uri.scheme == "file") {
            val file = File(checkNotNull(uri.path)); check(!file.exists() || file.delete())
        } else if (exists(raw)) check(DocumentsContract.deleteDocument(resolver, uri))
    }

    /** Remove only the recorded files. A user-added file prevents removal of its parent folder. */
    fun cleanup(root: String?, files: List<String>) {
        files.forEach(::delete)
        if (root == null || !exists(root)) return
        val children = children(root)
        children.filter { it.second == marker }.forEach { delete(it.first) }
        if (children(root).isEmpty()) delete(root)
    }

    fun cleanStaging(root: String) {
        if (!exists(root)) return
        val children = children(root)
        // Staging roots were created by this catalog. Never follow descendants or remove unrelated data.
        val owned = children.filter { (_, name) -> name == marker || Regex(".*-[a-f0-9]{12}\\.(ttf|otf|ttc|otc)").matches(name) }
        cleanup(root, owned.map { it.first })
    }

    private fun children(root: String): List<Pair<String, String>> {
        val uri = Uri.parse(root)
        if (uri.scheme == "file") return File(checkNotNull(uri.path)).listFiles()?.map { Uri.fromFile(it).toString() to it.name }
            ?: error("Cannot inspect font directory")
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
        return checkNotNull(resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), android.os.Bundle.EMPTY, null)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)).toString() to cursor.getString(1)) }
        }
    }

    companion object {
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
