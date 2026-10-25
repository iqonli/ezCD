package com.iqonli.ezcd.widget

import android.app.Application
import android.app.AlertDialog
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.fonts.*
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.ui.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.LocalDate
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontSmoothnessUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun settle() { repeat(4) {
        val events=CountDownLatch(1);CountdownIo.execute { events.countDown() };assertTrue(events.await(15,TimeUnit.SECONDS))
        val latch=CountDownLatch(1);FontIo.submit({ latch.countDown() }) { };assertTrue(latch.await(15,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle()
    } }
    private fun layout(v: View) { v.measure(View.MeasureSpec.makeMeasureSpec(432,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));v.layout(0,0,432,600) }
    private fun list(a: android.app.Activity)=all(a.window.decorView).filterIsInstance<ExpandableListView>().single()
    private fun catalog()=File(context.filesDir,"fonts/catalog.json")
    private fun seed(): ImportedFont {
        val file=File(context.cacheDir,"smooth.ttf")
        javaClass.getResourceAsStream("/font-fixtures/Regular.ttf")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        FontStore(context).switchStorage(FontStorageMode.REFERENCE)
        assertEquals(1,FontImporter(context).import(listOf(Uri.fromFile(file))).added)
        val original=FontStore(context).fonts().single()
        val root=JSONObject(catalog().readText());val sample=root.getJSONArray("fonts").getJSONObject(0).toString()
        val array=JSONArray()
        // Synthetic catalog metadata exercises a long list without shipping 60 font files.
        repeat(30) { family -> repeat(2) { face ->
            val item=JSONObject(sample).put("blob","%064x".format(family*2+face+1)).put("weight",if(face==0) 400 else 700)
            val name="Family %02d".format(family);val style=if(face==0) "Regular" else "Bold"
            item.getJSONObject("names").put("1",name).put("16",name).put("2",style).put("17",style).put("4","$name $style").put("6","Family$family-$style")
            array.put(item)
        } }
        catalog().writeText(root.put("fonts",array).toString());FontRuntime.invalidate()
        return original
    }
    private data class Position(val key: String,val child: Int,val top: Int)
    private fun position(list: ExpandableListView): Position {
        val packed=list.getExpandableListPosition(list.firstVisiblePosition)
        val index=ExpandableListView.getPackedPositionGroup(packed)
        return Position((list.expandableListAdapter.getGroup(index) as FontFamilyGroup).key,ExpandableListView.getPackedPositionChild(packed),list.getChildAt(0).top)
    }
    private fun scroll(a: android.app.Activity,index: Int=12) {
        val list=list(a);list.setSelectionFromTop(list.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(index)),-13);layout(a.window.decorView)
        assertTrue(list.firstVisiblePosition>0)
    }
    @Before fun reset() {
        settle();File(context.filesDir,"fonts").deleteRecursively();File(context.filesDir,"font-files").deleteRecursively()
        FontRuntime.invalidate();AppFonts.choose(context,null)
    }
    @Test fun managerAndSelectorKeepAdapterPositionAndExpansionWhenReturningFromPreview() {
        seed()
        for(type in listOf(FontManagerActivity::class.java,FontSelectorActivity::class.java)) {
            Robolectric.buildActivity(type).setup().use { c ->
                settle();val a=c.get();layout(a.window.decorView);val list=list(a);list.expandGroup(10);scroll(a)
                val before=position(list);val adapter=list.expandableListAdapter
                c.pause().resume();settle();layout(a.window.decorView)
                assertSame(adapter,list.expandableListAdapter);assertEquals(before,position(list));assertTrue(list.isGroupExpanded(10))
                val row=adapter.getGroupView(12,false,null,list)
                val arrow=all(row).filterIsInstance<ImageView>().single()
                val checkbox=all(row).filterIsInstance<CheckBox>().single()
                assertEquals(checkbox.layoutParams.width,arrow.layoutParams.width);assertEquals(checkbox.layoutParams.height,arrow.layoutParams.height)
                assertEquals(NativeUi.dp(a,32),arrow.layoutParams.width)
                arrow.performClick();layout(a.window.decorView);assertEquals(before,position(list))
                arrow.performClick();layout(a.window.decorView);assertEquals(before,position(list))
            }
        }
    }
    @Test fun changedCatalogKeepsTheSameVisibleFontEvenWhenANewFamilySortsBeforeIt() {
        seed()
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val a=c.get();layout(a.window.decorView);scroll(a);val list=list(a);val before=position(list);val adapter=list.expandableListAdapter
            val root=JSONObject(catalog().readText());val array=root.getJSONArray("fonts");val item=JSONObject(array.getJSONObject(0).toString()).put("blob","f".repeat(64))
            item.getJSONObject("names").put("1","AAA").put("16","AAA").put("4","AAA Regular");array.put(item)
            catalog().writeText(root.toString());FontRuntime.invalidate()
            c.pause().resume();settle();layout(a.window.decorView)
            assertSame(adapter,list.expandableListAdapter);assertEquals(31,adapter.groupCount);assertEquals(before,position(list))
        }
    }
    @Test fun previewRestoresItsVerticalPositionAfterRotationAndDoesNotFocusTheBottomInput() {
        seed();val font=FontRuntime.fonts(context).first()
        Robolectric.buildActivity(FontPreviewActivity::class.java,FontPreviewActivity.intent(context,font.id)).setup().use { c ->
            settle();layout(c.get().window.decorView)
            var scroll=all(c.get().window.decorView).filterIsInstance<ScrollView>().single()
            assertFalse(all(scroll).filterIsInstance<EditText>().single().hasFocus())
            scroll.scrollTo(0,330);assertEquals(330,scroll.scrollY)
            c.recreate();settle();layout(c.get().window.decorView)
            scroll=all(c.get().window.decorView).filterIsInstance<ScrollView>().single();assertEquals(330,scroll.scrollY)
        }
    }
    @Test fun homePageKeepsItsRowsAndScrollOnResumeAndRefreshesChangedTextTemplates() {
        val events=List(35) { i -> CountdownEvent("home-$i","Event %02d".format(i),LocalDate.of(2026,10,25),EventType.EVENT,createdAt=java.time.Instant.EPOCH) }
        CountdownRepository(context).save(events)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            settle();val a=c.get();layout(a.window.decorView);val list=all(a.window.decorView).filterIsInstance<ListView>().single()
            list.setSelectionFromTop(12,-10);layout(a.window.decorView)
            val before=list.firstVisiblePosition;val top=list.getChildAt(0).top;val adapter=list.adapter;val row=list.getChildAt(0)
            c.pause().resume();settle();layout(a.window.decorView)
            assertSame(adapter,list.adapter);assertEquals(before,list.firstVisiblePosition);assertEquals(top,list.getChildAt(0).top);assertSame(row,list.getChildAt(0))
            TextPool(context).save(mapOf("date" to "Changed date"));c.pause().resume();settle();layout(a.window.decorView)
            assertEquals(before,list.firstVisiblePosition);assertEquals(top,list.getChildAt(0).top)
            assertTrue(all(list.getChildAt(0)).filterIsInstance<TextView>().any { it.text.toString()=="Changed date" })
            TextPool(context).save(mapOf("date" to ""))
        }
    }
    @Test fun selectionAndRotationKeepThePartiallyVisibleChildAtItsOffset() {
        seed()
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();var a=c.get();layout(a.window.decorView);var list=list(a);list.expandGroup(12)
            list.setSelectionFromTop(list.getFlatListPosition(ExpandableListView.getPackedPositionForChild(12,1)),-9);layout(a.window.decorView)
            val before=position(list)
            list.onItemLongClickListener!!.onItemLongClick(list,list.getChildAt(0),list.firstVisiblePosition,0);layout(a.window.decorView)
            assertEquals(before,position(list))
            c.recreate();settle();a=c.get();layout(a.window.decorView);list=list(a)
            assertTrue(list.isGroupExpanded(12));assertEquals(before,position(list))
        }
    }
    @Test fun deletingTheVisibleFamilyKeepsItsNextNeighbourInsteadOfReturningToTheTop() {
        seed()
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val a=c.get();layout(a.window.decorView);scroll(a);val list=list(a);val before=position(list)
            list.onItemLongClickListener!!.onItemLongClick(list,list.getChildAt(0),list.firstVisiblePosition,0)
            FontManagerActivity::class.java.getDeclaredMethod("deleteSelected").apply { isAccessible=true }.invoke(a)
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            settle();layout(a.window.decorView)
            assertEquals("family 13",position(list).key);assertEquals(before.top,position(list).top)
        }
    }
    @Test fun rebindingRowsWithAnAppFontDoesNotResetTheirTypefaceOrRequestAnotherLayout() {
        seed();val font=FontRuntime.fonts(context).first();AppFonts.choose(context,font.id);settle()
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val list=list(c.get());val adapter=list.expandableListAdapter
            val row=adapter.getGroupView(0,false,null,list);layout(row)
            val label=all(row).filterIsInstance<TextView>().last();val face=label.typeface
            repeat(5) { adapter.getGroupView(0,false,row,list);AppFonts.apply(row) }
            assertSame(face,label.typeface);assertFalse(label.isLayoutRequested)
        }
    }
    @Test fun cachedPaintDoesNotWaitForTheFontCatalogIoMonitor() {
        seed();val font=FontRuntime.fonts(context).first();FontRuntime.resolve(context,font.id,400,false)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val holder=Thread { synchronized(FontRuntime) { entered.countDown();release.await(5,TimeUnit.SECONDS) } };holder.start();assertTrue(entered.await(5,TimeUnit.SECONDS))
        val executor=Executors.newSingleThreadExecutor()
        try { assertNotNull(executor.submit<android.text.TextPaint?> { FontRuntime.cachedPaint(font.id,400,false) }.get(1,TimeUnit.SECONDS)) }
        finally { release.countDown();holder.join(5000);executor.shutdownNow() }
    }
    @Test fun bitmapCompletionLaysOutImmediatelyAndIdenticalUpdatesReuseTheRenderedView() {
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val parent=FrameLayout(c.get());c.get().setContentView(parent)
            val preview=NativeWidgetPreview(c.get());parent.addView(preview,FrameLayout.LayoutParams(300,180));layout(c.get().window.decorView)
            val doc=WidgetLayoutStore(context).defaults().layouts.getValue("small")
            preview.update(null,LocalDate.of(2026,10,25),doc,180,100,true);settle()
            val image=preview.getChildAt(0);assertTrue(image is ImageView);assertTrue(image.width>0);assertTrue(image.height>0)
            preview.update(null,LocalDate.of(2026,10,25),doc,180,100,true);settle();assertSame(image,preview.getChildAt(0))
            parent.removeView(preview);parent.addView(preview);settle();assertSame(image,preview.getChildAt(0))
        }
    }
    @Test fun bitmapWorkCancelledByDetachRestartsOnAttachWithoutAnotherTap() {
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val parent=FrameLayout(c.get());c.get().setContentView(parent)
            val preview=NativeWidgetPreview(c.get());parent.addView(preview,FrameLayout.LayoutParams(300,180));layout(c.get().window.decorView)
            val started=CountDownLatch(1);val release=CountDownLatch(1)
            FontIo.submit({ started.countDown();release.await(5,TimeUnit.SECONDS) }) { };assertTrue(started.await(5,TimeUnit.SECONDS))
            try {
                preview.update(null,LocalDate.of(2026,10,25),WidgetLayoutStore(context).defaults().layouts.getValue("small"),180,100,true)
                parent.removeView(preview);parent.addView(preview)
            } finally { release.countDown() }
            settle();assertTrue(preview.getChildAt(0) is ImageView);assertTrue(preview.getChildAt(0).width>0)
        }
    }
    @Test fun ordinaryDialogsShareTypographyAndKeepMonospaceEditors() {
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val a=c.get();val body=LinearLayout(a);val small=TextView(a).apply { text="body";textSize=12f };body.addView(small)
            val code=EditText(a).apply { typeface=android.graphics.Typeface.MONOSPACE;textSize=12f };body.addView(code)
            val dialog=FontDialogBuilder(a).setTitle("Title").setMessage("Message").setView(body).setPositiveButton(android.R.string.ok,null).create()
            dialog.show();Shadows.shadowOf(Looper.getMainLooper()).idle();layout(dialog.window!!.decorView)
            val scale=a.resources.displayMetrics.scaledDensity
            assertEquals(16f,small.textSize/scale,.01f);assertEquals(12f,code.textSize/scale,.01f)
            assertEquals(14f,dialog.getButton(AlertDialog.BUTTON_POSITIVE).textSize/scale,.01f)
            assertEquals(16f,dialog.findViewById<TextView>(android.R.id.message).textSize/scale,.01f)
            assertEquals(20f,all(dialog.window!!.decorView).filterIsInstance<TextView>().single { it.text=="Title" }.textSize/scale,.01f)
            seed();AppFonts.choose(context,FontRuntime.fonts(context).first().id);settle();DialogUi.apply(dialog)
            assertEquals(12f,code.textSize/scale,.01f)
            dialog.dismiss()
            val custom=FocusAwareDialog(a);val field=EditText(a).apply { textSize=22f };custom.setView(field);custom.show()
            assertEquals(16f,field.textSize/scale,.01f);custom.dismiss()
            val choices=FontDialogBuilder(a).setSingleChoiceItems(arrayOf("Choice"),0,null).show();layout(choices.window!!.decorView)
            assertEquals(16f,all(choices.window!!.decorView).filterIsInstance<CheckedTextView>().single().textSize/scale,.01f);choices.dismiss()
        }
    }
}
