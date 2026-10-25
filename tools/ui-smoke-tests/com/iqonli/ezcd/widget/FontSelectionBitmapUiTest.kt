package com.iqonli.ezcd.widget

import android.app.Activity
import android.app.Application
import android.app.AlertDialog
import android.appwidget.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.*
import android.util.SizeF
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.fonts.*
import com.iqonli.ezcd.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.*
import java.util.concurrent.*
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],shadows=[WidgetConfigActivityUiTest.FrameworkTabHost::class,WidgetConfigActivityUiTest.FrameworkTabSpec::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FontSelectionBitmapUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private val event=CountdownEvent("bitmap-event","Bitmap event",LocalDate.of(2026,10,24),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun settle() {
        repeat(5) {
            val a=CountDownLatch(1);CountdownIo.execute { a.countDown() };assertTrue(a.await(15,TimeUnit.SECONDS))
            val b=CountDownLatch(1);FontIo.submit({ b.countDown() }) { };assertTrue(b.await(15,TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun fonts(vararg names: String): List<ImportedFont> {
        val store=FontStore(context);if(store.storage().mode==FontStorageMode.UNSELECTED)store.switchStorage(FontStorageMode.REFERENCE)
        names.forEach { name ->
            val file=File(context.cacheDir,"chosen-$name");javaClass.getResourceAsStream("/font-fixtures/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val report=FontImporter(context).import(listOf(Uri.fromFile(file)));assertEquals(report.issues.toString(),0,report.failures)
        }
        return store.fonts()
    }
    private fun deliver(activity: Activity,request: Int,code: Int,data: Intent?) {
        val owner=if(activity is WidgetConfigActivity) WidgetConfigActivity::class.java else FontManagerActivity::class.java
        owner.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,Intent::class.java).apply { isAccessible=true }.invoke(activity,request,code,data)
    }
    private fun capture(view: View,name: String,w: Int=432,h: Int=960) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));settle()
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val directory=File(System.getProperty("ezcd.preview.dir","build/font-previews")).apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun visibleDialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog().also {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val root=it.window!!.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(432,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,432,640)
    }
    @Before fun reset() {
        settle();File(context.filesDir,"fonts").deleteRecursively();File(context.filesDir,"font-files").deleteRecursively();FontRuntime.invalidate()
        AppFonts.choose(context,null);context.getSharedPreferences("font_preview",0).edit().clear().commit()
    }
    @Test fun selectorHasNoToolsOrLongPressAndFamilyRowOpensTheStandardFaceThenReturnsConfirmation() {
        val data=fonts("Bold.ttf","Light.ttf","Regular.ttf","Medium.ttf");val standard=data.single { FontStyle.weight(it)==400 }
        Robolectric.buildActivity(FontSelectorActivity::class.java).setup().use { c ->
            settle();val a=c.get();val menu=PopupMenu(a,a.window.decorView).menu;assertFalse(a.onCreateOptionsMenu(menu));assertEquals(0,menu.size())
            val list=all(a.window.decorView).filterIsInstance<ExpandableListView>().single();assertNull(list.onItemLongClickListener);assertFalse(list.isLongClickable);capture(a.window.decorView,"font-selector")
            list.performItemClick(list.expandableListAdapter.getGroupView(0,false,null,list),0,0)
            val next=Shadows.shadowOf(a).nextStartedActivityForResult
            assertEquals(standard.id,next.intent.getStringExtra(FontPreviewActivity.EXTRA_FONT_ID));assertTrue(next.intent.getBooleanExtra(FontSelectorActivity.EXTRA_SELECT,false))
            Robolectric.buildActivity(FontPreviewActivity::class.java,next.intent).setup().use { p ->
                settle();val preview=p.get();val actions=PopupMenu(preview,preview.window.decorView).menu;preview.onCreateOptionsMenu(actions)
                assertEquals(preview.getString(R.string.ez_font_select),actions.findItem(2).title.toString());assertNotNull(actions.findItem(1))
                preview.onOptionsItemSelected(actions.findItem(2));assertTrue(preview.isFinishing)
                val shadow=Shadows.shadowOf(preview);assertEquals(Activity.RESULT_OK,shadow.resultCode)
                deliver(a,next.requestCode,shadow.resultCode,shadow.resultIntent)
                assertTrue(a.isFinishing);assertEquals(standard.id,Shadows.shadowOf(a).resultIntent.getStringExtra(FontSelectorActivity.EXTRA_FONT_ID))
            }
        }
    }
    @Test fun selectorArrowExpandsOtherWeightsWithoutSelectingTheFamily() {
        val data=fonts("Regular.ttf","Bold.ttf")
        Robolectric.buildActivity(FontSelectorActivity::class.java).setup().use { c ->
            settle();val a=c.get();val list=all(a.window.decorView).filterIsInstance<ExpandableListView>().single()
            val row=list.expandableListAdapter.getGroupView(0,false,null,list);all(row).filterIsInstance<ImageView>().single().performClick()
            assertTrue(list.isGroupExpanded(0));assertNull(Shadows.shadowOf(a).nextStartedActivityForResult)
            val pos=list.getFlatListPosition(ExpandableListView.getPackedPositionForChild(0,1))
            list.performItemClick(list.expandableListAdapter.getChildView(0,1,true,null,list),pos,0)
            assertEquals(data.single { FontStyle.weight(it)==700 }.id,Shadows.shadowOf(a).nextStartedActivityForResult.intent.getStringExtra(FontPreviewActivity.EXTRA_FONT_ID))
        }
    }
    @Test fun appFamilyAutomaticallyUsesRegularAndBoldSkipsPreviewContentAndRestoresSystemStyles() {
        val data=fonts("Regular.ttf","Bold.ttf");val regular=data.single { FontStyle.weight(it)==400 }
        val root=LinearLayout(context);val normal=TextView(context);val bold=TextView(context).apply { setTypeface(Typeface.DEFAULT,Typeface.BOLD) }
        val skipped=TextView(context).apply { setTag(R.id.font_ui_skip,true);typeface=Typeface.MONOSPACE }
        root.addView(normal);root.addView(bold);root.addView(skipped);val original=bold.typeface
        AppFonts.choose(context,regular.id);settle();AppFonts.apply(root)
        assertEquals(400,normal.typeface.weight);assertEquals(700,bold.typeface.weight);assertFalse(bold.paint.isFakeBoldText);assertEquals(Typeface.MONOSPACE,skipped.typeface)
        AppFonts.choose(context,null);AppFonts.apply(root);assertEquals(original,bold.typeface);assertTrue(bold.typeface.isBold)
    }
    @Test fun appFamilyWithOnlyOneFaceKeepsBoldEmphasisAndDialogWindowsReceiveTheFont() {
        val regular=fonts("Light.ttf").single();AppFonts.choose(context,regular.id);settle()
        val bold=TextView(context).apply { setTypeface(Typeface.DEFAULT,Typeface.BOLD) };AppFonts.apply(bold)
        assertTrue(bold.paint.isFakeBoldText)
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val d=com.iqonli.ezcd.ui.FontDialogBuilder(c.get()).setMessage("Dialog font").setPositiveButton(android.R.string.ok,null).show()
            d.window!!.decorView.viewTreeObserver.dispatchOnGlobalLayout()
            assertEquals(300,all(d.window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()=="Dialog font" }.typeface.weight)
            d.dismiss()
        }
        AppFonts.choose(context,null);AppFonts.apply(bold);assertFalse(bold.paint.isFakeBoldText)
    }
    @Test fun fontSettingsAddsAppFontButtonsAndCancelKeepsSelectionWhileRestoreClearsIt() {
        val regular=fonts("Regular.ttf").single()
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val a=c.get();val menu=PopupMenu(a,a.window.decorView).menu;a.onCreateOptionsMenu(menu);a.onOptionsItemSelected(menu.findItem(2))
            var dialog=visibleDialog();capture(dialog.window!!.decorView,"font-settings",432,640)
            assertTrue(all(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text==a.getString(R.string.ez_font_app_display) })
            all(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text==a.getString(R.string.ez_font_system) }.performClick()
            val next=Shadows.shadowOf(a).nextStartedActivityForResult;assertEquals(FontSelectorActivity::class.java.name,next.intent.component!!.className)
            deliver(a,next.requestCode,Activity.RESULT_CANCELED,null);assertNull(AppFonts.selected(context))
            dialog=visibleDialog();all(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text==a.getString(R.string.ez_font_system) }.performClick()
            val again=Shadows.shadowOf(a).nextStartedActivityForResult
            deliver(a,again.requestCode,Activity.RESULT_OK,Intent().putExtra(FontSelectorActivity.EXTRA_FONT_ID,regular.id));settle()
            assertEquals(regular.id,AppFonts.selected(context));dialog=visibleDialog()
            all(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text==a.getString(R.string.ez_font_restore) }.performClick();assertNull(AppFonts.selected(context))
        }
    }
    @Test fun fontDeletionConfirmationIsOnlyDeleteQuestion() {
        fonts("Regular.ttf")
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val a=c.get();val list=all(a.window.decorView).filterIsInstance<ExpandableListView>().single();list.onItemLongClickListener!!.onItemLongClick(list,null,0,0)
            FontManagerActivity::class.java.getDeclaredMethod("deleteSelected").apply { isAccessible=true }.invoke(a)
            val dialog=ShadowAlertDialog.getLatestAlertDialog()
            val message=all(dialog.window!!.decorView).filterIsInstance<TextView>().first { it.id==android.R.id.message }
            assertEquals(a.getString(R.string.ez_library_delete_question),message.text.toString())
        }
    }
    @Test fun matchingPrefersRealFacesAndVariablesButSynthesizesItalicOnlyWhenNeeded() {
        val data=fonts("Light.ttf","Regular.ttf","Bold.ttf");val regular=data.single { FontStyle.weight(it)==400 }
        val matched=FontRuntime.match(data,650,false)!!;assertEquals(700,matched.weight)
        val ital=FontRuntime.match(data,400,true)!!;assertTrue(ital.syntheticItalic)
        val face=regular.copy(id="variable",metadata=regular.metadata.copy(axes=listOf(FontAxis("wght",300f,400f,700f),FontAxis("ital",0f,0f,1f))))
        val variable=FontRuntime.match(listOf(face),523,true)!!;assertTrue(variable.variations!!.contains("523.0"));assertFalse(variable.syntheticItalic)
        assertEquals(700,FontRuntime.match(listOf(face),900,false)!!.weight)
        val trueItalic=regular.copy(id="italic",metadata=regular.metadata.copy(italic=true))
        assertEquals("italic",FontRuntime.match(data+trueItalic,400,true)!!.font.id)
        val romanVariable=face.copy(metadata=face.metadata.copy(axes=face.metadata.axes.filter { it.tag=="wght" }))
        assertTrue(FontRuntime.match(listOf(romanVariable,trueItalic),523,true)!!.font.metadata.axes.none { it.tag=="wght" })
    }
    @Test fun bitmapKeepsActualDimensionsAndBackgroundOpacitySeparateFromTextOpacity() {
        val e=WidgetTextElement("custom","custom",content="AAAA",weight=700,color="#FF0000FF",top=50.0,width=80.0,height=70.0,maxSize=42.0)
        val layout=WidgetSizeLayout("#12345640",listOf(e));val image=BitmapWidgetRenderer.render(context,event,LocalDate.now(),layout,222,111)
        val d=context.resources.displayMetrics.density
        assertEquals((222*d).roundToInt(),image.width);assertEquals((111*d).roundToInt(),image.height)
        assertEquals(64,Color.alpha(image.getPixel(1,image.height/2)));assertEquals(0,Color.alpha(image.getPixel(0,0)))
        var textPixels=0
        for(y in 0 until image.height)for(x in 0 until image.width) { val c=image.getPixel(x,y);if(Color.red(c)>Color.blue(c)+80 && Color.alpha(c)>200) textPixels++ }
        assertTrue(textPixels>10);image.recycle()
        val font=fonts("Regular.ttf").single()
        val fallback=BitmapWidgetRenderer.fitLayout(context,"Bitmap event",e,75,25,16f,FontRuntime.paint(context,font.id,400,true))
        assertEquals(1,fallback.lineCount);assertTrue(fallback.getLineWidth(0)<=fallback.width+.01f);assertTrue(fallback.width<75);assertTrue(fallback.height<=25)
        val clear=BitmapWidgetRenderer.render(context,event,LocalDate.now(),layout.copy(background="#FFFFFF00"),222,111)
        assertEquals(0,Color.alpha(clear.getPixel(1,clear.height/2)));clear.recycle()
    }
    @Test fun bitmapRemoteViewsSurviveIpcAndOversizedVariantsStayWithinPixelBudget() {
        val layout=WidgetSizeLayout("#FFFFFF80",listOf(WidgetTextElement("custom","custom",content="Bitmap",top=50.0)))
        val views=BitmapWidgetRenderer.remoteViews(context,event,LocalDate.now(),layout,180,110)
        val parcel=Parcel.obtain();val copy=try { views.writeToParcel(parcel,0);parcel.setDataPosition(0);RemoteViews.CREATOR.createFromParcel(parcel) } finally { parcel.recycle() }
        val root=copy.apply(context,FrameLayout(context));assertNotNull(root.findViewById<ImageView>(R.id.widgetBitmap));assertTrue(all(root).none { it is TextView })
        val image=BitmapWidgetRenderer.render(context,event,LocalDate.now(),layout,1000000,1,maxPixels=1000)
        assertTrue(image.width.toLong()*image.height<=1000);image.recycle()
    }
    @Test fun desktopRendererUsesReportedSizeAndRebuildsAfterResize() {
        settle();CountdownRepository(context).save(listOf(event));val manager=AppWidgetManager.getInstance(context)
        val info=AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) };Shadows.shadowOf(manager).addBoundWidget(81,info)
        WidgetPreferences(context).save(81,event.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
        WidgetLayoutStore(context).save(81,event.id,WidgetLayoutStore(context).defaults().copy(renderer="bitmap"))
        fun update(w: Int,h: Int) {
            manager.updateAppWidgetOptions(81,Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(w.toFloat(),h.toFloat()))) })
            CountdownWidgetProvider.updateWidget(context,manager,81)
            val view=Shadows.shadowOf(manager).getViewFor(81);val bitmap=(view.findViewById<ImageView>(R.id.widgetBitmap).drawable as BitmapDrawable).bitmap
            assertEquals((w*context.resources.displayMetrics.density).roundToInt(),bitmap.width);assertEquals((h*context.resources.displayMetrics.density).roundToInt(),bitmap.height)
        }
        update(222,111);update(170,140)
    }
    @Test fun editorTogglesModeShowsStaticWeightsAndPersistsFontsAndItalicAcrossRecreationAndSave() {
        val data=fonts("Light.ttf","Regular.ttf","Bold.ttf");val light=data.single { FontStyle.weight(it)==300 }
        val entry=WidgetLibraryStore(context).create(WidgetLayoutStore(context).defaults(),event)
        val intent=Intent(context,WidgetLibraryEditorActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id)
        Robolectric.buildActivity(WidgetLibraryEditorActivity::class.java,intent).setup().use { c ->
            settle();var a=c.get();assertNull(a.window.decorView.findViewWithTag<View>("widget_font"))
            a.window.decorView.findViewWithTag<Button>("widget_renderer").performClick();settle()
            assertNotNull(a.window.decorView.findViewWithTag<CheckBox>("widget_italic"))
            a.window.decorView.findViewWithTag<Button>("widget_font").performClick();val picker=Shadows.shadowOf(a).nextStartedActivityForResult
            deliver(a,picker.requestCode,Activity.RESULT_OK,Intent().putExtra(FontSelectorActivity.EXTRA_FONT_ID,light.id));settle()
            val weights=a.window.decorView.findViewWithTag<Spinner>("widget_weight_options");assertEquals(3,weights.count);weights.setSelection(2);Shadows.shadowOf(Looper.getMainLooper()).idle();settle()
            a.window.decorView.findViewWithTag<CheckBox>("widget_italic").performClick();settle();capture(a.window.decorView,"widget-bitmap-editor")
            assertEquals(a.getString(R.string.ez_font_italic_synthetic),a.window.decorView.findViewWithTag<CheckBox>("widget_italic").text.toString())
            val renderingLabel=all(a.window.decorView).filterIsInstance<TextView>().single { it.text==a.getString(R.string.ez_widget_renderer) }
            val renderingButton=a.window.decorView.findViewWithTag<Button>("widget_renderer")
            assertTrue(renderingLabel.width>0);assertSame(renderingLabel.parent,renderingButton.parent);assertTrue(renderingButton.left>=renderingLabel.right)
            c.recreate();settle();a=c.get();assertTrue(a.window.decorView.findViewWithTag<CheckBox>("widget_italic").isChecked)
            assertEquals(a.getString(R.string.ez_widget_bitmap),a.window.decorView.findViewWithTag<Button>("widget_renderer").text.toString())
            val menu=PopupMenu(a,a.window.decorView).menu;a.onCreateOptionsMenu(menu);a.onOptionsItemSelected(menu.findItem(R.id.widget_save_action));settle()
            assertTrue(a.isFinishing);val saved=WidgetLibraryStore(context).get(entry.id)!!.document;assertEquals("bitmap",saved.renderer)
            val selected=saved.layouts.getValue("small").elements.first();assertEquals(light.id,selected.fontId);assertEquals(700,selected.weight);assertTrue(selected.italic)
            assertEquals(saved,WidgetLayoutCodec.decode(WidgetLayoutCodec.encode(saved)))
        }
    }
    @Test fun actualVariableFontChangesPixelsAndOffersItsContinuousRangeInTheEditor() {
        val variable=fonts("WeightVariable.ttf").single()
        assertEquals(FontAxis("wght",300f,400f,700f),variable.metadata.axes.single())
        fun pixels(weight: Int): Int {
            val e=WidgetTextElement("sample","custom",content="AAAA",fontId=variable.id,weight=weight,color="#FF0000FF",top=50.0,width=90.0,height=80.0,maxSize=42.0)
            val image=BitmapWidgetRenderer.render(context,null,LocalDate.now(),WidgetSizeLayout("#FFFFFF00",listOf(e)),222,111)
            var n=0;for(y in 0 until image.height)for(x in 0 until image.width) if(Color.alpha(image.getPixel(x,y))>200) n++
            image.recycle();return n
        }
        val light=pixels(300);val bold=pixels(700);assertTrue("Variable outlines must change: $light / $bold",bold>light*1.05)
        val entry=WidgetLibraryStore(context).create(WidgetLayoutStore(context).defaults(),event)
        val intent=Intent(context,WidgetLibraryEditorActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id)
        Robolectric.buildActivity(WidgetLibraryEditorActivity::class.java,intent).setup().use { c ->
            settle();val a=c.get();a.window.decorView.findViewWithTag<Button>("widget_renderer").performClick();settle()
            a.window.decorView.findViewWithTag<Button>("widget_font").performClick();val picker=Shadows.shadowOf(a).nextStartedActivityForResult
            deliver(a,picker.requestCode,Activity.RESULT_OK,Intent().putExtra(FontSelectorActivity.EXTRA_FONT_ID,variable.id));settle()
            assertNull(a.window.decorView.findViewWithTag<View>("widget_weight_options"))
            val field=all(a.window.decorView).filterIsInstance<EditText>().single { it.contentDescription==a.getString(R.string.ez_layout_weight) }
            field.setText("523");assertNull(field.error);field.setText("800");assertNotNull(field.error);field.setText("523")
            a.window.decorView.findViewWithTag<Button>("widget_renderer").performClick();settle();assertNull(a.window.decorView.findViewWithTag<View>("widget_font"));assertNull(a.window.decorView.findViewWithTag<View>("widget_italic"))
            a.window.decorView.findViewWithTag<Button>("widget_renderer").performClick();settle()
            assertEquals("523",all(a.window.decorView).filterIsInstance<EditText>().single { it.contentDescription==a.getString(R.string.ez_layout_weight) }.text.toString())
        }
    }
}
