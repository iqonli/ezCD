// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui
import android.app.Activity
import android.content.Intent
import com.iqonli.ezcd.R
internal object FilePickers {
    fun open(activity: Activity,request: Int) {
        val intent=Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivityForResult(Intent.createChooser(intent,activity.getString(R.string.ez_layout_from_file)),request)
    }
}
