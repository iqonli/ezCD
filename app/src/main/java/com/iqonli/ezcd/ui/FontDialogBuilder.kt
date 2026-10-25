// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui
import android.app.AlertDialog
import android.content.Context
/** Dialog windows do not belong to the activity's view tree. */
internal class FontDialogBuilder(context: Context) : AlertDialog.Builder(context) {
    override fun create(): AlertDialog = super.create().also { dialog -> dialog.setOnShowListener { DialogUi.apply(dialog) } }
    override fun show(): AlertDialog = super.show().also { DialogUi.apply(it) }
}
