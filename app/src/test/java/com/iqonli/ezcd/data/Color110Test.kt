package com.iqonli.ezcd.data
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class Color110Test {
 @Test fun customColorOriginRoundTripsEvenWhenItMatchesAPreset() {
  val e=CountdownEvent("color","Color",LocalDate.of(2026,1,1),EventType.EVENT,countColor=OpaqueColor.BLUE,countColorCustom=true,createdAt=Instant.EPOCH)
  assertEquals(e,CountdownStorageCodec.decode(CountdownStorageCodec.encode(listOf(e))).single())
  assertNotEquals(EventRevision.of(e),EventRevision.of(e.copy(countColorCustom=false)))
 }
 @Test fun widgetDefaultAndFollowingEventAreDistinctAndRoundTrip() {
  val doc=WidgetLayoutDocument(WidgetLayoutSize.entries.associate { it.key to WidgetSizeLayout("default",WidgetLayoutCodec.builtins.map { source -> WidgetTextElement(source,source,color="default") }) })
  assertEquals(doc,WidgetLayoutCodec.decode(WidgetLayoutCodec.encode(doc)))
 }
}
