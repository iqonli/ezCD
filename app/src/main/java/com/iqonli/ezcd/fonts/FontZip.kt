// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.FilterInputStream
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Use the central directory: legacy Chinese names and stored data descriptors are common. */
internal object FontZip {
    class Archive(private val file: File,private val zip: ZipFile,val entries: List<ZipEntry>,private val charset: Charset) {
        private val repeated=entries.groupingBy { it.name }.eachCount().filterValues { it>1 }.keys
        fun <T> read(index: Int,consume: (InputStream)->T): T {
            val entry=entries[index]
            // ZipFile looks entries up by name; an ordinal is needed for duplicate names.
            if(entry.name in repeated) return ZipInputStream(file.inputStream().buffered(),charset).use { input ->
                var skipped=0L
                for(i in 0..index) {
                    checkNotNull(input.nextEntry)
                    if(i==index) return@use consume(input)
                    val buffer=ByteArray(64*1024)
                    while(true) { val n=input.read(buffer);if(n<0) break;skipped+=n;require(skipped<=FontImporter.MAX_BATCH_BYTES) }
                    input.closeEntry()
                }
                error("ZIP entry missing")
            }
            return CheckedInputStream(zip.getInputStream(entry),CRC32()).use { input ->
                val verified=object: FilterInputStream(input) {
                    fun checked(n: Int): Int {
                        if(n<0) check(entry.crc<0 || input.checksum.value==entry.crc) { "ZIP entry CRC mismatch" }
                        return n
                    }
                    override fun read()=checked(input.read())
                    override fun read(b: ByteArray,off: Int,len: Int)=checked(input.read(b,off,len))
                }
                val result=consume(verified)
                check(entry.crc<0 || input.checksum.value==entry.crc) { "ZIP entry CRC mismatch" }
                result
            }
        }
    }
    fun <T> open(context: Context,uri: Uri,progress: (Long)->Unit={},stream: InputStream?=null,consume: (Archive)->T): T {
        fun use(file: File): T {
            var error: Exception?=null
            for(name in listOf("UTF-8","GB18030","IBM437")) {
                val charset=Charset.forName(name)
                val opened=try {
                    val zip=ZipFile(file,charset)
                    try {
                        val entries=zip.entries().asSequence().take(10001).toList()
                        zip to entries
                    } catch(e: Exception) { zip.close();throw e }
                } catch(e: java.util.zip.ZipException) { error=e;continue }
                  catch(e: IllegalArgumentException) { error=e;continue }
                return opened.first.use { zip ->
                    require(opened.second.size<=10000) { "Too many archive entries" }
                    consume(Archive(file,zip,opened.second,charset))
                }
            }
            throw checkNotNull(error)
        }
        if(uri.scheme=="file") {
            val file=File(checkNotNull(uri.path))
            require(file.length()<=FontImporter.MAX_BATCH_BYTES) { "ZIP size limit exceeded" }
            return use(file)
        }
        // Providers may supply a non-seekable pipe. Keep its staging file scoped to this call.
        return FontCache.withFile(context) { file ->
            fun copy(input: InputStream) {
                file.outputStream().use { output ->
                    val buffer=ByteArray(64*1024);var bytes=0L
                    while(true) {
                        val n=input.read(buffer);if(n<0) break
                        bytes+=n;require(bytes<=FontImporter.MAX_BATCH_BYTES) { "ZIP size limit exceeded" }
                        output.write(buffer,0,n);progress(bytes)
                    }
                }
            }
            if(stream!=null) copy(stream) else checkNotNull(context.contentResolver.openInputStream(uri)).use(::copy)
            use(file)
        }
    }
}
