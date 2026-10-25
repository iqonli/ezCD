// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

internal data class WidgetPinSize(val columns: Int=1,val rows: Int=1) {
    init { require(columns in 1..MAX_PRESET && rows in 1..MAX_PRESET) }
    val metadataKey get()="ezcd.widget.size.${columns}x${rows}"
    val preview get()=WidgetPreviewDimensions(56+(columns-1)*70,50+(rows-1)*70)
    companion object { const val MAX_PRESET=16 }
}

internal data class DesktopGrid(val columns: Int,val rows: Int) {
    companion object {
        fun parse(raw: String?): DesktopGrid? {
            val match=Regex("^\\s*(\\d{1,2})\\s*[xX×,]\\s*(\\d{1,2})\\s*$").matchEntire(raw.orEmpty()) ?: return null
            val x=match.groupValues[1].toInt();val y=match.groupValues[2].toInt()
            return if(x in 1..64 && y in 1..64) DesktopGrid(x,y) else null
        }
    }
}
