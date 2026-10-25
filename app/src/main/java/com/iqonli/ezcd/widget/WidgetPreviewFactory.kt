// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget
import android.content.Context
import android.widget.RemoteViews
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.model.CountdownEvent
import java.time.LocalDate
internal object WidgetPreviewFactory {
    fun remoteViews(context: Context): RemoteViews = NativeWidgetRenderer.remoteViews(context,null,CountdownTime.snapshot().today,WidgetLayoutStore(context).defaults().layouts.getValue("small"),84,84)
    fun remoteViews(context: Context,event: CountdownEvent?,style: WidgetStyleSelection,today: LocalDate=CountdownTime.snapshot().today,dimensions: WidgetPreviewDimensions=WidgetPreviewSizing.representative(WidgetSize.STANDARD),document: WidgetLayoutDocument?=null,placeholder: Boolean=false): RemoteViews {
        if(placeholder) return RemoteViews(context.packageName,com.iqonli.ezcd.R.layout.widget_placeholder)
        val document=document ?: WidgetLayoutStore(context).load(-1,event?.id)
        val layout=document.layouts.getValue(WidgetLayoutSize.fromDimensions(dimensions.widthDp,dimensions.heightDp).key)
        return if(document.renderer=="bitmap") BitmapWidgetRenderer.remoteViews(context,event,today,layout,dimensions.widthDp,dimensions.heightDp) else NativeWidgetRenderer.remoteViews(context,event,today,layout,dimensions.widthDp,dimensions.heightDp)
    }
}
