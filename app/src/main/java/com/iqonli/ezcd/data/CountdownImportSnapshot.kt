// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class CountdownImportSnapshot private constructor(
    private val file: File,
    val identity: String,
) {
    constructor(file: File) : this(file, file.name)

    fun write(payload: String) {
        require(payload.toByteArray(Charsets.UTF_8).size <= BackupArchiveCodec.MAX_BYTES)
        val parent = file.parentFile ?: throw IOException("Import snapshot has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create import snapshot directory")
        }

        val temporary = temporaryFile()
        temporary.delete()
        try {
            FileOutputStream(temporary).use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun readPayload(): String = file.inputStream().use { BackupArchiveCodec.read(it) }

    /** Consume approval before replacing data, so stale saved UI cannot offer it again. */
    fun claimPayload(): String {
        val payload = readPayload()
        Files.move(file.toPath(), claimedFile().toPath(), StandardCopyOption.REPLACE_EXISTING)
        return payload
    }

    fun exists(): Boolean = file.isFile

    fun clear() {
        file.delete()
        temporaryFile().delete()
        claimedFile().delete()
    }

    private fun temporaryFile(): File = File(file.parentFile, "${file.name}.tmp")
    private fun claimedFile(): File = File(file.parentFile, "${file.name}.applying")

    companion object {
        fun create(context: Context): CountdownImportSnapshot {
            val identity = UUID.randomUUID().toString()
            return CountdownImportSnapshot(snapshotFile(context, identity), identity)
        }

        fun restore(context: Context, identity: String): CountdownImportSnapshot? {
            val canonicalIdentity = runCatching { UUID.fromString(identity).toString() }.getOrNull()
                ?: return null
            if (canonicalIdentity != identity) return null
            return CountdownImportSnapshot(snapshotFile(context, identity), identity)
        }

        private fun snapshotFile(context: Context, identity: String): File =
            File(context.cacheDir, "pending-ezcd-import-$identity.json")
    }
}
