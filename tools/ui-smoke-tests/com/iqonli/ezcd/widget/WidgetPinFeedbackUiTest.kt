package com.iqonli.ezcd.widget
import android.app.*
import android.appwidget.*
import android.content.*
import android.os.*
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAppWidgetManager
import java.time.*
import java.util.concurrent.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],shadows=[WidgetPinFeedbackUiTest.Launcher::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetPinFeedbackUiTest {
    @Implements(AppWidgetManager::class)
    class Launcher: ShadowAppWidgetManager() {
        companion object {
            var gate: CountDownLatch?=null
            var queryGate: CountDownLatch?=null
            var called=CountDownLatch(1)
            var queried=CountDownLatch(1)
            var ids=intArrayOf()
            var supported=true
            var reject=false
            var failure: RuntimeException?=null
            var mainThread=false
            var extras: Bundle?=null
        }
        @Implementation override fun isRequestPinAppWidgetSupported()=supported
        @Implementation override fun getAppWidgetIds(component: ComponentName): IntArray {
            queryGate?.let { queried.countDown();it.await(12,TimeUnit.SECONDS) }
            return ids
        }
        @Implementation override fun requestPinAppWidget(component: ComponentName,options: Bundle?,callback: PendingIntent?): Boolean {
            extras=options;mainThread=Looper.myLooper()==Looper.getMainLooper();called.countDown()
            gate?.await(12,TimeUnit.SECONDS);failure?.let { throw it };return !reject
        }
    }
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private fun flush() { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun event()=CountdownEvent("pin", "Pin",LocalDate.now(),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun open(): Activity { Launcher.ids=intArrayOf();Launcher.supported=true;Launcher.reject=false;Launcher.failure=null;Launcher.gate=null;Launcher.queryGate=null;Launcher.called=CountDownLatch(1);Launcher.queried=CountDownLatch(1);flush();CountdownRepository(context).save(listOf(event()));return Robolectric.buildActivity(Activity::class.java).setup().get() }
    @Test fun pinRequestUsesForegroundActivityAndThe105NativePreviewContract() {
        val a=open();val feedback=WidgetPinFeedback(a)
        try {
            feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event())
            assertTrue(Launcher.called.await(5,TimeUnit.SECONDS));assertTrue(Launcher.mainThread)
            val extras=checkNotNull(Launcher.extras)
            @Suppress("DEPRECATION") val preview=extras.getParcelable<android.widget.RemoteViews>(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW)
            assertNotNull(preview)
            val view=preview!!.apply(context,android.widget.FrameLayout(context))
            assertNotNull(view.findViewById<android.view.View>(com.iqonli.ezcd.R.id.nativeTexts))
        } finally { feedback.onDestroy() }
    }
    @Test fun acceptedPinWithoutCallbackShowsUnconfirmedDetailsInsteadOfClaimingFailure() {
        val a=open();val feedback=WidgetPinFeedback(a)
        try {
            feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event())
            assertTrue(Launcher.called.await(5,TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            assertFalse(feedback.pending);val dialog=awaitDialog();assertTrue(dialogText(dialog).contains(a.getString(com.iqonli.ezcd.R.string.ez_widget_pin_unconfirmed)))
        } finally { feedback.onDestroy() }
    }
    @Test fun blockedLauncherQueryAlsoHasAnIndependentUiDeadline() {
        val a=open();Launcher.queryGate=CountDownLatch(1);val feedback=WidgetPinFeedback(a)
        try {
            feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event())
            assertTrue(Launcher.queried.await(5,TimeUnit.SECONDS));assertTrue(Launcher.called.await(5,TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            assertFalse(feedback.pending);val dialog=awaitDialog();assertTrue(dialogText(dialog).contains(a.getString(com.iqonli.ezcd.R.string.ez_widget_pin_unconfirmed)))
        } finally { Launcher.queryGate?.countDown();feedback.onDestroy() }
    }
    @Test fun rejectedAndExceptionalRequestsExposeUsefulCopyableDiagnostics() {
        for(throwError in listOf(false,true)) {
            val a=open();Launcher.reject=!throwError
            if(throwError) Launcher.failure=SecurityException("test launcher permission denied")
            val feedback=WidgetPinFeedback(a)
            try {
                feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event())
                val dialog=awaitDialog();val detail=dialogText(dialog)
                assertTrue(detail.contains("Android="));assertTrue(detail.contains("Launcher="));assertTrue(detail.contains("previewBytes="))
                if(throwError) assertTrue(detail.contains("SecurityException: test launcher permission denied"))
                else assertTrue(detail.contains("requestPinAppWidget returned=false"))
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
                val copied=context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
                assertEquals(detail,copied)
            } finally { feedback.onDestroy();a.finish() }
        }
    }
    @Test fun unsupportedLauncherExplainsSupportResult() {
        val a=open();Launcher.supported=false;val feedback=WidgetPinFeedback(a)
        try { feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event());assertTrue(dialogText(awaitDialog()).contains("pinSupported=false")) }
        finally { feedback.onDestroy() }
    }
    @Test fun failedCallbackBindingIsNotSilentlyIgnored() {
        val a=open();val feedback=WidgetPinFeedback(a)
        try {
            feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event());flush()
            val state=Bundle();feedback.save(state);val attempt=checkNotNull(WidgetPinRequestSnapshot.fromCallbackData(state.getString("pinSnapshot")))
            WidgetPinDiagnostics.note(context,attempt.requestToken,"callback binding result=INVALID_WIDGET_ID")
            WidgetPinAttemptStore.record(context,attempt.requestToken,false)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            assertTrue(dialogText(awaitDialog()).contains("INVALID_WIDGET_ID"))
        } finally { feedback.onDestroy() }
    }
    @Test fun returningFromLauncherAllowsTheSuccessCallbackToFinishBeforeWarning() {
        val a=open();val feedback=WidgetPinFeedback(a)
        try {
            feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event());flush()
            val state=Bundle();feedback.save(state);val attempt=checkNotNull(WidgetPinRequestSnapshot.fromCallbackData(state.getString("pinSnapshot")))
            feedback.onWindowFocusChanged(false);feedback.onPause()
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
            feedback.onResume();feedback.onWindowFocusChanged(true)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertTrue(feedback.pending);assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog())
            WidgetPinAttemptStore.record(context,attempt.requestToken,true)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            assertFalse(feedback.pending);assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog())
        } finally { feedback.onDestroy() }
    }
    @Test fun miuiZeroCallbackRecoversOnlyTheSingleNewWidget() {
        val a=open();Launcher.ids=intArrayOf(298,299,302);val feedback=WidgetPinFeedback(a)
        try {
            feedback.onResume();feedback.onWindowFocusChanged(true)
            val field=WidgetPinFeedback::class.java.getDeclaredField("baseline").apply { isAccessible=true }
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(field.get(feedback)==null && System.nanoTime()<until) { Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10) }
            assertNotNull(field.get(feedback));feedback.request(event());flush()
            val state=Bundle();feedback.save(state);val attempt=checkNotNull(WidgetPinRequestSnapshot.fromCallbackData(state.getString("pinSnapshot")))
            Launcher.ids=intArrayOf(298,299,302,303)
            Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(303,AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) })
            context.sendBroadcast(Intent(context,WidgetPinResultReceiver::class.java).setData(android.net.Uri.parse(attempt.callbackData())).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0))
            Shadows.shadowOf(Looper.getMainLooper()).idle();flush();flush()
            assertEquals(event().id,WidgetPreferences(context).get(303)?.eventId)
            for(id in listOf(298,299,302)) assertNull(WidgetPreferences(context).get(id))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9));assertFalse(feedback.pending)
            assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog())
        } finally { feedback.onDestroy() }
    }
    @Test fun permissionRestrictionCannotLeaveTheAddButtonPendingWithoutWindowFocus() {
        val a=open();val feedback=WidgetPinFeedback(a)
        try { feedback.onResume();feedback.onWindowFocusChanged(true);feedback.request(event());feedback.onWindowFocusChanged(false)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));assertFalse(feedback.pending)
        } finally { feedback.onDestroy() }
    }
    private fun dialogText(dialog: AlertDialog): String {
        fun all(v: android.view.View): List<android.view.View> = listOf(v)+if(v is android.view.ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
        return all(dialog.window!!.decorView).filterIsInstance<android.widget.TextView>().first { it.text.contains("Android=") }.text.toString()
    }
    private fun awaitDialog(): AlertDialog {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(System.nanoTime()<until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("Missing diagnostic dialog")
    }

}
