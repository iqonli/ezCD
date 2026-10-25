// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

class ShareImageProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String {
        shareFile(uri, requireExists = true)
        return MIME_TYPE
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Share images are read-only")
        return ParcelFileDescriptor.open(
            shareFile(uri, requireExists = true),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = shareFile(uri, requireExists = true)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                },
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun shareFile(uri: Uri, requireExists: Boolean): File {
        val providerContext = context ?: throw FileNotFoundException("Provider is not attached")
        if (uri.scheme != "content" || uri.authority != authority(providerContext)) {
            throw FileNotFoundException("Unexpected share URI")
        }
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != PATH_CARD) {
            throw FileNotFoundException("Unexpected share path")
        }
        val fileName = segments[1]
        if (!FILE_NAME_PATTERN.matches(fileName)) {
            throw FileNotFoundException("Unexpected share filename")
        }

        val directory = ShareImageStore.directory(providerContext).canonicalFile
        val file = File(directory, fileName).canonicalFile
        if (file.parentFile != directory || (requireExists && !file.isFile)) {
            throw FileNotFoundException("Share image not found")
        }
        return file
    }

    companion object {
        private const val MIME_TYPE = "image/png"
        private const val PATH_CARD = "card"
        private val FILE_NAME_PATTERN = Regex("""ezcd-share-[0-9]+-[0-9a-fA-F-]+\.png""")

        internal fun authority(context: Context): String = "${context.packageName}.share"

        internal fun uriFor(context: Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(PATH_CARD)
            .appendPath(file.name)
            .build()
    }
}

internal object ShareImageStore {
    private const val DIRECTORY_NAME = "share-cards"
    private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun write(context: Context, bitmap: Bitmap): Uri {
        val directory = directory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create share cache")
        }
        prune(directory)

        val file = File(
            directory,
            "ezcd-share-${System.currentTimeMillis()}-${UUID.randomUUID()}.png",
        )
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode share card"
                }
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return ShareImageProvider.uriFor(context, file)
    }

    internal fun directory(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)

    fun prune(context: Context) = prune(directory(context))

    private fun prune(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach(File::delete)
    }
}
