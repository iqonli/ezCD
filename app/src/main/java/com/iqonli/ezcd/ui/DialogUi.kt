// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.app.Dialog
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R

/** Keep native dialog structure and picker internals; normalize ordinary text only. */
internal object DialogUi {
    fun apply(dialog: Dialog) {
        val root=dialog.window?.decorView ?: return
        val title=root.resources.getIdentifier("alertTitle","id","android")
        fun visit(view: View) {
            if(view.getTag(R.id.font_ui_skip)==true || view is DatePicker || view is TimePicker || view is NumberPicker) return
            if(view is TextView) {
                if(view.typeface===android.graphics.Typeface.MONOSPACE) view.setTag(R.id.dialog_keep_text_size,true)
                val size=when {
                    view.id==title && title!=0 || view.getTag(R.id.dialog_heading)==true -> 20f
                    view is Button -> 14f
                    view.getTag(R.id.dialog_keep_text_size)==true -> null
                    else -> 16f
                }
                if(size!=null && kotlin.math.abs(view.textSize-size*view.resources.displayMetrics.scaledDensity)>.1f)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP,size)
                if(view is Button) { NativeUi.flat(view);view.isAllCaps=false }
            }
            if(view is ListView && view.getTag(R.id.dialog_list_styled)!=true) {
                view.setTag(R.id.dialog_list_styled,true)
                view.setOnHierarchyChangeListener(object: ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?,child: View?) { child?.let { visit(it);com.iqonli.ezcd.fonts.AppFonts.apply(it) } }
                    override fun onChildViewRemoved(parent: View?,child: View?)=Unit
                })
            }
            if(view is ViewGroup) for(i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        com.iqonli.ezcd.fonts.AppFonts.watch(dialog.window)
    }
}
