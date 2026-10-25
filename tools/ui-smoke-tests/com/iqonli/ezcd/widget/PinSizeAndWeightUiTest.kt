package com.iqonli.ezcd.widget

import android.app.*
import android.appwidget.*
import android.content.*
import android.content.pm.*
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.text.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.fonts.FontRuntime
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.*
import java.time.*
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],shadows=[PinSizeAndWeightUiTest.Launcher::class,PinSizeAndWeightUiTest.Ops::class])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PinSizeAndWeightUiTest {
    @Implements(AppWidgetManager::class)
    class Launcher: ShadowAppWidgetManager() {
        companion object { val keys=mutableListOf<String?>();var active: String?=null;var pinned: String?=null;var calls=0;var reject=false;var extras: Bundle?=null }
        @Implementation override fun isRequestPinAppWidgetSupported()=true
        @Implementation fun updateAppWidgetProviderInfo(provider: ComponentName,key: String?) { active=key;keys+=key }
        @Implementation override fun requestPinAppWidget(provider: ComponentName,options: Bundle?,callback: PendingIntent?): Boolean { calls++;pinned=active;extras=options;if(reject) throw SecurityException("Denied");return true }
        @Implementation override fun getAppWidgetIds(provider: ComponentName)=intArrayOf()
    }
    @Implements(AppOpsManager::class)
    class Ops {
        companion object { var mode=AppOpsManager.MODE_DEFAULT }
        @Implementation fun checkOpNoThrow(op: Int,uid: Int,pkg: String): Int { assertEquals(10017,op);return mode }
    }
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private val event=CountdownEvent("sized","高考加油",LocalDate.now().plusDays(100),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun settle() { repeat(4) { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));val font=CountDownLatch(1);com.iqonli.ezcd.fonts.FontIo.submit({ font.countDown() }) { };assertTrue(font.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() } }
    private fun layout(v: View) { v.measure(View.MeasureSpec.makeMeasureSpec(432,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,432,800) }
    private fun desktop(a: WidgetPinSetupActivity,state: DesktopState) {
        WidgetPinSetupActivity::class.java.getDeclaredField("desktop").apply { isAccessible=true }.set(a,state)
        WidgetPinSetupActivity::class.java.getDeclaredMethod("updateControls").apply { isAccessible=true }.invoke(a)
    }
    @Before fun reset() { Ops.mode=AppOpsManager.MODE_DEFAULT;Launcher.keys.clear();Launcher.active=null;Launcher.pinned=null;Launcher.calls=0;Launcher.reject=false;Launcher.extras=null;CountdownRepository(context).save(listOf(event)) }
    @Test fun libraryRequestFreezesOnlyTheNewWidgetsLayout() {
        val original=WidgetLayoutStore(context).defaults()
        val document=original.copy(renderer="bitmap")
        val snapshot=WidgetPinRequestSnapshot.create(event.id,WidgetStyleSelection(WidgetAppearance.LIGHT,WidgetBackground.CLASSIC)).copy(customLayout=true)
        assertEquals(snapshot,WidgetPinRequestSnapshot.fromCallbackData(snapshot.callbackData()))
        WidgetPinAttemptStore.document(context,snapshot.requestToken,document)
        assertEquals(WidgetPinFinalizeResult.APPLIED,WidgetPinFinalizer.finalize(context,901,snapshot))
        assertEquals(document,WidgetLayoutStore(context).load(901,event.id))
        assertEquals(original,WidgetLayoutStore(context).load(-1,event.id))
        WidgetPinAttemptStore.clear(context,snapshot.requestToken)
        assertEquals(WidgetPinFinalizeResult.ALREADY_MATCHING,WidgetPinFinalizer.finalize(context,901,snapshot))
    }
    @Test fun placeholderBindsWithoutAnEventAndOpensWidgetEditorOnTap() {
        val snapshot=WidgetPinRequestSnapshot.create(WidgetPinning.PLACEHOLDER_ID,WidgetStyleSelection(WidgetAppearance.LIGHT,WidgetBackground.CLASSIC))
        assertEquals(WidgetPinFinalizeResult.APPLIED,WidgetPinFinalizer.finalize(context,902,snapshot))
        val render=CountdownWidgetProvider.Companion::class.java.declaredMethods.single { it.name=="renderForSize" }.apply { isAccessible=true }
        val views=render.invoke(CountdownWidgetProvider.Companion,context,context,902,LocalDate.now(),WidgetPreferences(context).get(902),WidgetRenderData.Ready(emptyMap(),null),84,84,100000L) as RemoteViews
        Robolectric.buildActivity(Activity::class.java).setup().use { c ->
            val root=views.apply(c.get(),FrameLayout(c.get()))
            assertEquals("ezCD+",(root as TextView).text.toString())
            assertTrue(root.performClick())
            val intent=Shadows.shadowOf(context).nextStartedActivity
            assertEquals(WidgetConfigActivity::class.java.name,intent.component!!.className)
            assertEquals(902,intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0))
        }
    }
    @Test fun libraryPageHasStaticPreviewAndAllowsUnselectedEventAcrossRotation() {
        val entry=WidgetLibraryStore(context).create(WidgetLayoutStore(context).defaults())
        val intent=Intent(context,WidgetPinSetupActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id)
        Robolectric.buildActivity(WidgetPinSetupActivity::class.java,intent).setup().use { c ->
            settle();var a=c.get();layout(a.window.decorView)
            assertNotNull(a.window.decorView.findViewWithTag<NativeWidgetPreview>("pin_layout_preview"))
            assertTrue(a.window.decorView.findViewWithTag<Button>("pin_add").isEnabled)
            assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("某些系统") || it.text.toString().contains("Some systems") })
            c.recreate();settle();a=c.get();layout(a.window.decorView)
            assertTrue(a.window.decorView.findViewWithTag<Button>("pin_placeholder").isEnabled)
            a.window.decorView.findViewWithTag<Button>("pin_add").performClick()
            assertEquals(1,Launcher.calls)
        }
    }
    @Test fun defaultBitmapAndNativeUseTheSameWeightFamiliesVariationsAndInk() {
        for(weight in listOf(100,300,400,500,650,700,800,900)) {
            val native=NativeWidgetRenderer.paint(context,weight);val bitmap=FontRuntime.paint(context,"",weight,false)
            assertEquals(native.typeface,bitmap.typeface);assertEquals(native.fontVariationSettings,bitmap.fontVariationSettings);assertEquals(native.isFakeBoldText,bitmap.isFakeBoldText)
            assertEquals(native.textSkewX,bitmap.textSkewX,0f)
            fun ink(p: TextPaint): IntArray {
                val image=Bitmap.createBitmap(200,80,Bitmap.Config.ARGB_8888);p.textSize=36f;p.color=Color.BLACK
                Canvas(image).drawText("Aa 0123",5f,55f,p)
                val pixels=IntArray(200*80);image.getPixels(pixels,0,200,0,0,200,80);image.recycle();return pixels
            }
            assertArrayEquals(ink(native),ink(bitmap))
        }
        assertTrue(FontRuntime.paint(context,"",700,true).typeface.isItalic)
    }
    @Test fun chosenProviderSizeIsScopedToOneRequestAndResetsEvenOnFailure() {
        Robolectric.buildActivity(Activity::class.java).setup().use { c ->
            val a=c.get();val snapshot=WidgetPinRequestSnapshot.create(event.id,WidgetStyleSelection(WidgetAppearance.LIGHT,WidgetBackground.CLASSIC),size=WidgetPinSize(3,2))
            assertTrue(WidgetPinning.request(a,event,snapshot));assertEquals("ezcd.widget.size.3x2",Launcher.pinned);assertNull(Launcher.active)
            assertEquals(listOf("ezcd.widget.size.3x2",null),Launcher.keys)
            val options=Launcher.extras!!.getBundle(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS)!!
            assertEquals(196,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));assertEquals(120,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
            Launcher.reject=true;assertThrows(SecurityException::class.java) { WidgetPinning.request(a,event,snapshot) };assertNull(Launcher.active)
        }
    }
    @Test fun addPagePreservesDraftsEnforcesReportedGridAndDisablesAtBounds() {
        val intent=Intent(context,WidgetPinSetupActivity::class.java).putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID,event.id)
        Robolectric.buildActivity(WidgetPinSetupActivity::class.java,intent).setup().use { c ->
            settle();var a=c.get();desktop(a,DesktopState(DesktopGrid(4,6)));layout(a.window.decorView)
            assertTrue(a.window.decorView.findViewWithTag<TextView>("pin_summary").text.contains("高考加油"))
            var width=a.window.decorView.findViewWithTag<EditText>("pin_columns");var height=a.window.decorView.findViewWithTag<EditText>("pin_rows")
            width.setText("4");height.setText("6");assertFalse(all(width.parent as View).filterIsInstance<Button>().single { it.text=="+1" }.isEnabled)
            width.setText("5");assertNotNull(width.error);assertFalse(a.window.decorView.findViewWithTag<Button>("pin_add").isEnabled)
            width.setText("3");height.setText("2");c.recreate();settle();a=c.get();desktop(a,DesktopState(DesktopGrid(4,6)))
            width=a.window.decorView.findViewWithTag("pin_columns");height=a.window.decorView.findViewWithTag("pin_rows")
            assertEquals("3",width.text.toString());assertEquals("2",height.text.toString())
            a.window.decorView.findViewWithTag<Button>("pin_add").performClick();assertEquals("ezcd.widget.size.3x2",Launcher.pinned)
            assertFalse(width.isEnabled);assertFalse(a.window.decorView.findViewWithTag<Button>("pin_add").isEnabled)
        }
    }
    @Test fun deniedPermissionShowsAnActionableDialogWithoutCreatingAPinRequest() {
        val intent=Intent(context,WidgetPinSetupActivity::class.java).putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID,event.id)
        Robolectric.buildActivity(WidgetPinSetupActivity::class.java,intent).setup().use { c ->
            settle();val a=c.get();desktop(a,DesktopState(permission=DesktopPermission.DENIED,miui=true))
            assertTrue(a.window.decorView.findViewWithTag<TextView>("pin_note").text.contains(a.getString(R.string.ez_pin_permission_denied)))
            a.window.decorView.findViewWithTag<Button>("pin_add").performClick();assertEquals(0,Launcher.calls)
            val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing);assertEquals(a.getString(R.string.ez_pin_open_permissions),dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertTrue(a.window.decorView.findViewWithTag<Button>("pin_add").isEnabled)
        }
        assertEquals(DesktopPermission.UNKNOWN,DesktopAccess.permissionMode(AppOpsManager.MODE_DEFAULT))
        assertEquals(DesktopPermission.DENIED,DesktopAccess.permissionMode(AppOpsManager.MODE_IGNORED))
        assertEquals(DesktopPermission.ALLOWED,DesktopAccess.permissionMode(AppOpsManager.MODE_ALLOWED))
    }
    @Test fun readsMiuiGridAndShortcutRestrictionWithoutTreatingUnknownAsAllowed() {
        val home=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info=ResolveInfo().apply { activityInfo=ActivityInfo().apply { packageName="com.miui.home";name="Launcher" } }
        Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(home,info)
        Settings.System.putString(context.contentResolver,"miui_home_screen_cells_size","4x6")
        Ops.mode=AppOpsManager.MODE_IGNORED
        val state=DesktopAccess.detect(context)
        assertEquals(DesktopGrid(4,6),state.grid);assertEquals(DesktopPermission.DENIED,state.permission)
        Ops.mode=AppOpsManager.MODE_DEFAULT;assertEquals(DesktopPermission.UNKNOWN,DesktopAccess.detect(context).permission)
        Ops.mode=AppOpsManager.MODE_ALLOWED;assertEquals(DesktopPermission.ALLOWED,DesktopAccess.detect(context).permission)
    }
    @Test fun appEntryPointsOpenTheSizePageAndTheEditorSavesFirst() {
        val intent=Intent(context,EditorActivity::class.java).putExtra("event_id",event.id)
        Robolectric.buildActivity(EditorActivity::class.java,intent).setup().use { c ->
            settle();val a=c.get();val name=all(a.window.decorView).filterIsInstance<EditText>().first { it.text.toString()==event.title }
            name.setText("Saved before pinning")
            all(a.window.decorView).filterIsInstance<Button>().single { it.text==a.getString(R.string.ez_add_widget) }.performClick();settle()
            val next=Shadows.shadowOf(a).nextStartedActivity
            assertEquals(WidgetPinSetupActivity::class.java.name,next.component!!.className);assertEquals(0,Launcher.calls)
            assertEquals("Saved before pinning",(CountdownRepository(context).loadResult() as CountdownLoadResult.Success).events.single().title)
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { c ->
            settle();val a=c.get();val list=all(a.window.decorView).filterIsInstance<ListView>().single()
            list.onItemLongClickListener!!.onItemLongClick(list,null,0,0)
            ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null,1,1)
            assertEquals(WidgetPinSetupActivity::class.java.name,Shadows.shadowOf(a).nextStartedActivity.component!!.className)
        }
    }
    @Test fun settingsAndAboutHave36spBrandAndAboutLinksShareARow() {
        for(type in listOf(SettingsActivity::class.java,AboutActivity::class.java)) Robolectric.buildActivity(type).setup().use { c ->
            settle();val a=c.get();layout(a.window.decorView)
            val brand=all(a.window.decorView).filterIsInstance<TextView>().single { it.text=="ezCD" }
            assertEquals(36f,brand.textSize/a.resources.displayMetrics.scaledDensity,.01f)
            if(a is AboutActivity) {
                val buttons=all(a.window.decorView).filterIsInstance<Button>();assertSame(buttons.single { it.text=="GitHub" }.parent,buttons.single { it.text==a.getString(R.string.ez_licenses) }.parent)
                val bitmap=Bitmap.createBitmap(432,800,Bitmap.Config.ARGB_8888);a.window.decorView.draw(Canvas(bitmap))
                val file=java.io.File(System.getProperty("ezcd.preview.dir"),"about-1.0.16.png");file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
        }
    }
}
