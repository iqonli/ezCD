// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui
internal object CompactTimeInput {
    fun parse(raw: String): Int? {
        if(raw.length!=4 || raw.any { it !in '0'..'9' }) return null
        val h=raw.substring(0,2).toInt();val m=raw.substring(2).toInt()
        return if(h in 0..23 && m in 0..59) h*60+m else null
    }
}
