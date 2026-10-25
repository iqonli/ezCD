// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.EventIcon
import com.iqonli.ezcd.model.RepeatRule
import java.time.LocalDate

/**
 * Shares the same localized card or text fallback from a saved Home event or an editor draft.
 */
internal object EventShareLauncher {
    fun share(
        activity: Activity,
        title: String,
        date: LocalDate,
        icon: EventIcon,
        repeatRule: RepeatRule,
        countMode: CountMode,
        onFinished: () -> Unit = {},
    ) {
        val today = CountdownTime.snapshot().today
        val text = ShareCardContentFactory.text(activity, title, date, repeatRule, today, countMode)
        val renderContext = ShareCardRenderer.captureContext(activity)
        val content = ShareCardContentFactory.create(
            context = renderContext, title = title, date = date, icon = icon,
            repeatRule = repeatRule, today = today, countMode = countMode,
        )
        val dark = ShareCardRenderer.resolveDark(activity)
        val appContext = activity.applicationContext

        CountdownIo.submit(
            task = {
                val bitmap = ShareCardRenderer.render(renderContext, content, dark)
                try { ShareImageStore.write(appContext, bitmap) } finally { bitmap.recycle() }
            },
            onComplete = { result ->
                if (activity.isFinishing || activity.isDestroyed) return@submit
                onFinished()
                val uri = result.getOrNull()
                if (uri != null && launch(activity, imageIntent(activity, title, text, uri))) return@submit
                if (!launch(activity, Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text))) {
                    Toast.makeText(activity, R.string.share_unavailable, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun imageIntent(activity: Activity, title: String, text: String, uri: Uri) =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newUri(activity.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun launch(activity: Activity, intent: Intent): Boolean = runCatching {
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_share)))
        true
    }.getOrDefault(false)
}
