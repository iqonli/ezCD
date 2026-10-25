package com.iqonli.ezcd.fonts
import org.junit.Assert.*
import org.junit.Test
class FontStyleTest {
    private fun font(style: String,raw: Int=400,file: String="Test-$style.ttf",id: String=style) = ImportedFont(id,"blob",file,
        FontMetadata(0,mapOf(1 to "Test",2 to style,4 to "Test $style",6 to "Test-$style"),raw,false))
    @Test fun namesRecoverWeightsWhenEveryInternalClassClaimsRegular() {
        val styles=listOf("Black","Regular","ExtraLight","SemiBold","Light","ExtraBold","Medium","Thin","Bold")
        assertEquals(listOf(100,200,300,400,500,600,700,800,900),FontCatalog.groups(styles.map { font(it) }).single().fonts.map(FontStyle::weight))
        assertEquals("Regular",FontCatalog.groups(styles.map { font(it) }).single().representative.metadata.subfamily)
    }
    @Test fun numberedAndChineseStylesUseUsefulFallbacksWithoutTreatingFamilyHeiAsHeavy() {
        assertEquals(300,FontStyle.weight(font("W3")))
        assertEquals(600,FontStyle.weight(font("半粗")))
        assertEquals(200,FontStyle.weight(font("特细")))
        assertEquals(300,FontStyle.weight(font("Unknown",3)))
        val hei=font("Regular").copy(metadata=FontMetadata(0,mapOf(1 to "黑体",2 to "Regular",4 to "黑体"),400,false),fileName="黑体.ttf")
        assertEquals(400,FontStyle.weight(hei))
    }
    @Test fun postscriptAndFilenameFallbacksSupportCamelCaseAndUnderscores() {
        val regular=font("Regular").copy(metadata=FontMetadata(0,mapOf(1 to "Test",2 to "Regular",4 to "Test",6 to "Test-ExtraBold"),400,false))
        assertEquals(800,FontStyle.weight(regular))
        assertEquals(700,FontStyle.weight(font("Unknown",400,"Test_Bold_Italic.ttf")))
        assertTrue(FontStyle.italic(font("Unknown",400,"Test_Bold_Italic.ttf")))
        assertTrue(FontStyle.compareNames("weight2","weight10")<0)
    }
    @Test fun variableDefaultsAndAnUprightStandardFaceAreRespected() {
        val variable=font("Regular").copy(metadata=font("Regular").metadata.copy(axes=listOf(FontAxis("wght",200f,500f,900f))))
        assertEquals(500,FontStyle.weight(variable))
        val italic=font("Italic",400);val normal=font("Medium",500)
        assertEquals(normal.id,FontStyle.standard(listOf(italic,normal)).id)
    }
}
