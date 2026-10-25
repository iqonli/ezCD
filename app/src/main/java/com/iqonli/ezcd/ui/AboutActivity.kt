// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
import com.iqonli.ezcd.R

class AboutActivity : BaseActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = getString(R.string.ez_about)
        val body = NativeUi.page(this)
        NativeUi.label(body, "ezCD", 36f)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        NativeUi.label(body, getString(R.string.ez_version, version), 14f)
        fun space() { body.addView(View(this),LinearLayout.LayoutParams(1,NativeUi.dp(this,16))) }
        space()
        NativeUi.label(body, getString(R.string.ez_intro))
        val links=LinearLayout(this);body.addView(links,LinearLayout.LayoutParams(-1,-2))
        NativeUi.button(links, "GitHub") { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/iqonli/ezCD"))) }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        NativeUi.button(links, getString(R.string.ez_licenses)) {
            val contents = listOf("NOTICE.txt", "MIT.txt", "Apache-2.0-CountAway.txt", "OFL-NotoSans.txt").joinToString("\n\n") { file ->
                assets.open("licenses/$file").bufferedReader().use { it.readText() }
            }
            val text = TextView(this).apply {
                this.text = contents; textSize = 13f; setTextIsSelectable(true)
                setPadding(24, 16, 24, 16)
            }
            com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_licenses)
                .setView(android.widget.ScrollView(this).apply { addView(text) })
                .setPositiveButton(android.R.string.ok, null).show()
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        space()
        NativeUi.label(body, getString(R.string.ez_credits), 18f)
        NativeUi.label(body, getString(R.string.ez_upstream))
    }
}
