package com.iqonli.ezcd.fonts

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FontMetadataTest {
    private fun <T> fixture(name: String, block: (File) -> T): T {
        val file = File.createTempFile("ezcd-font-test-", ".ttf")
        return try {
            javaClass.getResourceAsStream("/font-fixtures/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            block(file)
        } finally { file.delete() }
    }

    @Test fun readsActualFullNameSubfamilyAndWeightRatherThanFilename() = fixture("Bold.ttf") { file ->
        val font = FontMetadataReader.read(file).single()
        assertEquals("ezCD Fixture Bold", font.fullName)
        assertEquals("ezCD Fixture", font.family)
        assertEquals("Bold", font.subfamily)
        assertEquals(700, font.weight)
        assertEquals("MIT", font.names[13])
        assertFalse(font.italic)
        assertTrue(font.axes.isEmpty())
    }

    @Test fun fourWeightsBecomeOneGroupWithFullNameOnParentAndOrderedChildren() {
        val faces = listOf("Bold", "Light", "Regular", "Medium").mapIndexed { i, name ->
            fixture("$name.ttf") { ImportedFont("$i", "$i", "$name.ttf", FontMetadataReader.read(it).single()) }
        }
        val group = FontCatalog.groups(faces).single()
        assertEquals("ezCD Fixture Regular", group.label)
        assertFalse(group.single)
        assertEquals(listOf(300, 400, 500, 700), group.fonts.map { it.metadata.weight })
    }

    @Test fun singleFaceRemainsSingleAndCollectionsExposeTheirDistinctFaces() = fixture("Family.ttc") { file ->
        val faces = FontMetadataReader.read(file)
        assertEquals(listOf(0, 1), faces.map { it.faceIndex })
        assertEquals(listOf("Regular", "Bold"), faces.map { it.subfamily })
        val one = ImportedFont("one", "blob", "Family.ttc", faces.first())
        assertTrue(FontCatalog.groups(listOf(one)).single().single)
    }

    @Test fun variableFontRangeComesFromTheFile() = fixture("VariableMetadata.ttf") { file ->
        assertEquals(FontAxis("wght", 300f, 400f, 700f), FontMetadataReader.read(file).single().axes.single())
    }

    @Test fun typographicNamesKeepLegacyWeightFamiliesTogether() {
        val a = FontMetadata(0, mapOf(1 to "Example Light", 2 to "Regular", 4 to "Example Light", 16 to "Example", 17 to "Light"), 300, false)
        val b = FontMetadata(0, mapOf(1 to "Example", 2 to "Regular", 4 to "Example Regular", 16 to "Example", 17 to "Regular"), 400, false)
        assertEquals(a.familyKey, b.familyKey)
        assertEquals("Light", a.subfamily)
    }

    @Test fun truncatedFilesAndOutOfBoundsTablesAreRejected() = fixture("Regular.ttf") { file ->
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(16))
        assertThrows(IllegalArgumentException::class.java) { FontMetadataReader.read(file) }
        file.writeBytes(bytes)
        java.io.RandomAccessFile(file, "rw").use { it.seek(20); it.writeInt(-1) }
        assertThrows(IllegalArgumentException::class.java) { FontMetadataReader.read(file) }
        Unit
    }

    @Test fun collectionFaceCountCannotAllocateAnUnboundedList() = fixture("Family.ttc") { file ->
        java.io.RandomAccessFile(file, "rw").use { it.seek(8); it.writeInt(Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { FontMetadataReader.read(file) }
        Unit
    }
}
