// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Location and records commit together. Old managed bytes are removed only after that commit. */
class FontStore(context: Context) {
    private val context = context.applicationContext
    private val directory = File(this.context.filesDir, "fonts")
    private val index = AtomicFile(File(directory, "catalog.json"))
    private val files = FontFiles(this.context)
    private data class Cleanup(val root: String?, val files: List<String>)
    private data class State(val storage: FontStorage = FontStorage(), val fonts: List<ImportedFont> = emptyList(),
        val cleanup: List<Cleanup> = emptyList(), val staging: String? = null)

    fun fonts(): List<ImportedFont> = synchronized(lock) { recover().fonts }
    fun storage(): FontStorage = synchronized(lock) { recover().storage }
    fun find(id: String): ImportedFont? = fonts().firstOrNull { it.id == id }
    fun typeface(font: ImportedFont, variations: String? = null): android.graphics.Typeface = synchronized(lock) {
        val current = readState().fonts.firstOrNull { it.id == font.id } ?: error("Font was removed")
        files.typeface(checkNotNull(current.source), current.metadata.faceIndex, variations)
    }
    fun reference(uri: Uri): FontSource = files.reference(uri)

    fun install(file: File, originalName: String, source: FontSource? = null): Pair<Int, Int> = synchronized(lock) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        val hash = FontFiles.hex(digest.digest())
        var state = recover()
        check(state.storage.mode != FontStorageMode.UNSELECTED) { context.getString(com.iqonli.ezcd.R.string.ez_font_storage_required) }
        check(state.staging == null) { "A previous font operation still needs cleanup" }
        val origin = source ?: FontSource(Uri.fromFile(file).toString())
        val metadata = FontMetadataReader.read(file)
        metadata.forEach { face -> checkNotNull(android.graphics.Typeface.Builder(file).setTtcIndex(face.faceIndex).build()) }
        val old = state.fonts.filter { it.blob == hash }
        if (old.isNotEmpty() && (state.storage.mode == FontStorageMode.REFERENCE ||
                old.first().source?.let { runCatching { files.exists(it.uri) }.getOrDefault(false) } == true)) {
            val currentSource = if (state.storage.mode == FontStorageMode.REFERENCE) origin else checkNotNull(old.first().source)
            val restored = metadata.map { ImportedFont("$hash:${it.faceIndex}", hash, old.first().fileName, it, currentSource) }
            require(state.fonts.count { it.blob != hash } + restored.size <= MAX_FONTS) { "Too many imported fonts" }
            writeState(state.copy(fonts = state.fonts.filterNot { it.blob == hash } + restored))
            return@synchronized restored.count { font -> old.none { it.id == font.id } } to old.size
        }
        val name = originalName.replace('\\', '/').substringAfterLast('/').filter { !it.isISOControl() }.take(256).ifBlank { "font.ttf" }
        var additions = metadata.map { ImportedFont("$hash:${it.faceIndex}", hash, name, it, origin) }
        require(state.fonts.count { it.blob != hash } + additions.size <= MAX_FONTS) { "Too many imported fonts" }
        if (state.storage.mode == FontStorageMode.REFERENCE) {
            writeState(state.copy(fonts = state.fonts.filterNot { it.blob == hash } + additions))
        } else {
            var freshRoot: String? = null
            var created: String? = null
            try {
                val root = state.storage.root ?: files.createRoot(state.storage.mode, state.storage.directory).also {
                    freshRoot = it; state = state.copy(staging = it); writeState(state)
                }
                val destination = files.copy(FontSource(Uri.fromFile(file).toString()), root, additions.first())
                created = destination
                additions = additions.map { it.copy(source = FontSource(destination)) }
                writeState(state.copy(storage = state.storage.copy(root = root), fonts = state.fonts.filterNot { it.blob == hash } + additions, staging = null))
            } catch (error: Exception) {
                val cleaned = runCatching { if (freshRoot != null) files.cleanStaging(checkNotNull(freshRoot)) else created?.let(files::delete) }.isSuccess
                runCatching { writeState(state.copy(staging = if (cleaned) null else freshRoot)) }
                throw error
            }
        }
        additions.size to 0
    }

    fun switchStorage(mode: FontStorageMode, parent: String? = null, deleteDirectory: Boolean = false, progress: (Int) -> Unit = {}): FontChangeResult = synchronized(lock) {
        require(mode != FontStorageMode.UNSELECTED)
        progress(0)
        val before = recover()
        check(before.staging == null) { "A previous font operation still needs cleanup" }
        if (mode == before.storage.mode && (mode != FontStorageMode.DIRECTORY || parent == before.storage.directory)) return@synchronized FontChangeResult(before.cleanup.size)
        if (mode == FontStorageMode.REFERENCE) {
            val deleting = before.storage.mode == FontStorageMode.INTERNAL || deleteDirectory
            val cleanup = if (deleting) listOf(cleanupFor(before)) else emptyList()
            writeState(before.copy(storage = FontStorage(FontStorageMode.REFERENCE), fonts = emptyList(), cleanup = before.cleanup + cleanup))
        } else {
            require(mode != FontStorageMode.DIRECTORY || !parent.isNullOrBlank())
            val root = files.createRoot(mode, parent)
            var committed = false
            try {
                writeState(before.copy(staging = root))
                val distinct = before.fonts.distinctBy { it.blob }
                val targets = distinct.mapIndexed { i, font ->
                    val destination = files.copy(checkNotNull(font.source), root, font)
                    progress(5 + (i + 1) * 85 / distinct.size)
                    font.blob to destination
                }.toMap()
                val migrated = before.fonts.map { it.copy(source = FontSource(targets.getValue(it.blob))) }
                val cleanup = if (before.storage.mode == FontStorageMode.REFERENCE) emptyList() else listOf(cleanupFor(before))
                writeState(before.copy(storage = FontStorage(mode, if (mode == FontStorageMode.DIRECTORY) parent else null, root), fonts = migrated,
                    cleanup = before.cleanup + cleanup, staging = null))
                committed = true
            } finally {
                if (!committed) {
                    val cleaned = runCatching { files.cleanStaging(root) }.isSuccess
                    runCatching { writeState(if (cleaned) before else before.copy(staging = root)) }
                }
            }
        }
        val result = FontChangeResult(recover().cleanup.size)
        progress(100)
        result
    }

    fun delete(ids: Set<String>, progress: (Int) -> Unit = {}): FontChangeResult = synchronized(lock) {
        progress(0)
        val state = recover()
        val remaining = state.fonts.filterNot { it.id in ids }
        val live = remaining.mapNotNull { it.source?.uri }.toSet()
        val removed = state.fonts.filter { it.id in ids }.mapNotNull { it.source?.uri }.distinct().filterNot { it in live }
        val cleanup = if (state.storage.mode == FontStorageMode.REFERENCE || removed.isEmpty()) emptyList() else listOf(Cleanup(null, removed))
        writeState(state.copy(fonts = remaining, cleanup = state.cleanup + cleanup))
        progress(80)
        val result = FontChangeResult(recover().cleanup.size)
        progress(100)
        result
    }
    private fun cleanupFor(state: State) = Cleanup(state.storage.root, state.fonts.mapNotNull { it.source?.uri }.distinct())

    private fun recover(): State {
        var state = readState()
        state.staging?.let { root ->
            if (runCatching { files.cleanStaging(root) }.isSuccess) { state = state.copy(staging = null); writeState(state) }
        }
        if (state.cleanup.isNotEmpty()) {
            val pending = state.cleanup.filter { runCatching { files.cleanup(it.root, it.files) }.isFailure }
            if (pending != state.cleanup) { state = state.copy(cleanup = pending); writeState(state) }
        }
        return state
    }

    private fun readState(): State {
        if (!index.baseFile.exists() && !File(index.baseFile.path + ".bak").exists()) return State()
        val raw = index.openRead().use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= MAX_INDEX_BYTES); out.write(buffer, 0, n) }
            out.toString("UTF-8")
        }
        val root = JSONObject(raw); val version = root.getInt("version"); require(version in 1..2)
        val array = root.getJSONArray("fonts"); require(array.length() <= MAX_FONTS)
        val fonts = List(array.length()) { i ->
            val item = array.getJSONObject(i); val blob = item.getString("blob"); val face = item.getInt("face")
            require(blob.matches(Regex("[a-f0-9]{64}")) && face in 0 until FontMetadataReader.MAX_FACES)
            val names = item.getJSONObject("names"); val axes = item.getJSONArray("axes"); require(axes.length() <= 64)
            val metadata = FontMetadata(face, names.keys().asSequence().associate { it.toInt() to names.getString(it) }, item.getInt("weight"), item.getBoolean("italic"),
                List(axes.length()) { n -> val a = axes.getJSONObject(n); FontAxis(a.getString("tag"), a.getDouble("min").toFloat(), a.getDouble("default").toFloat(), a.getDouble("max").toFloat()) })
            val source = if (version == 1) FontSource(Uri.fromFile(File(directory, "$blob.font")).toString()) else
                item.getJSONObject("source").let { s -> FontSource(s.getString("uri"), s.optional("entry"), s.optInt("entryIndex", -1)) }
            ImportedFont("$blob:$face", blob, item.getString("filename"), metadata, source)
        }
        if (version == 1) return State(FontStorage(FontStorageMode.INTERNAL), fonts)
        val cleanup = root.getJSONArray("cleanup"); require(cleanup.length() <= MAX_FONTS)
        return State(FontStorage(FontStorageMode.valueOf(root.getString("mode")), root.optional("directory"), root.optional("root")), fonts,
            List(cleanup.length()) { i -> val job = cleanup.getJSONObject(i); val paths = job.getJSONArray("files"); require(paths.length() <= MAX_FONTS)
                Cleanup(job.optional("root"), List(paths.length()) { paths.getString(it) }) }, root.optional("staging"))
    }

    private fun writeState(state: State) {
        check(directory.isDirectory || directory.mkdirs())
        val raw = JSONObject().put("version", 2).put("mode", state.storage.mode.name)
            .put("directory", state.storage.directory ?: JSONObject.NULL).put("root", state.storage.root ?: JSONObject.NULL)
            .put("staging", state.staging ?: JSONObject.NULL)
            .put("cleanup", JSONArray().apply { state.cleanup.forEach { job -> put(JSONObject().put("root", job.root ?: JSONObject.NULL).put("files", JSONArray(job.files))) } })
            .put("fonts", JSONArray().apply { state.fonts.forEach { f ->
                val source = checkNotNull(f.source)
                put(JSONObject().put("blob", f.blob).put("face", f.metadata.faceIndex).put("filename", f.fileName)
                    .put("weight", f.metadata.weight).put("italic", f.metadata.italic)
                    .put("source", JSONObject().put("uri", source.uri).put("entry", source.zipEntry ?: JSONObject.NULL).put("entryIndex", source.zipIndex))
                    .put("names", JSONObject().apply { f.metadata.names.forEach { (id, text) -> put(id.toString(), text) } })
                    .put("axes", JSONArray().apply { f.metadata.axes.forEach { a -> put(JSONObject().put("tag", a.tag).put("min", a.minimum).put("default", a.default).put("max", a.maximum)) } }))
            } }).toString().toByteArray(Charsets.UTF_8)
        require(raw.size <= MAX_INDEX_BYTES)
        val stream = index.startWrite()
        try { stream.write(raw); index.finishWrite(stream); FontRuntime.invalidate() } catch (error: Exception) { index.failWrite(stream); throw error }
    }

    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)
    companion object {
        private val lock = Any()
        private const val MAX_INDEX_BYTES = 8 * 1024 * 1024
        private const val MAX_FONTS = 2048
    }
}
