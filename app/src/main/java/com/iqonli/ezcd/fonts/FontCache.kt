// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import java.io.File

/** Only font work files are disposable; drafts and shared originals have separate lifetimes. */
internal object FontCache {
    private val active = mutableSetOf<String>()
    private fun directory(context: Context) = File(context.cacheDir, "font-work")
    fun clean(context: Context) = synchronized(active) {
        val owned = directory(context)
        owned.listFiles()?.filter { it.isFile && it.path !in active }?.forEach { it.delete() }
        if (owned.listFiles()?.isEmpty() == true) owned.delete()
        context.cacheDir.listFiles()?.filter {
            it.isFile && it.path !in active && Regex("font-(preview-)?[0-9]+\\.tmp").matches(it.name)
        }?.forEach { it.delete() }
    }
    fun <T> withFile(context: Context, use: (File) -> T): T {
        val file = synchronized(active) {
            val folder = directory(context); check(folder.isDirectory || folder.mkdirs())
            File.createTempFile("font-", ".tmp", folder).also { active += it.path }
        }
        return try { use(file) } finally {
            synchronized(active) { file.delete(); active -= file.path; file.parentFile?.let { if (it.listFiles()?.isEmpty() == true) it.delete() } }
        }
    }
}
