// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.content.Context
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class TextPool(context: Context) {
    private val prefs=context.getSharedPreferences("text_pool",Context.MODE_PRIVATE)
    fun get(key: String)=prefs.getString(key,"").orEmpty()
    fun save(values: Map<String,String>) { check(prefs.edit().apply { values.forEach { (k,v) -> putString(k,v) } }.commit()) }
    companion object {
        val keys=listOf("date","left","today","passed")
        fun date(context: Context,date: LocalDate,style: FormatStyle=FormatStyle.MEDIUM): String {
            val template=TextPool(context).get("date")
            return if(template.isEmpty()) date.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(context.resources.configuration.locales[0]))
            else expandDate(template,date)
        }
        fun expandDate(template: String,date: LocalDate)=template.replace("%yyyy%",date.year.toString().padStart(4,'0'))
            .replace("%mm%",date.monthValue.toString().padStart(2,'0')).replace("%dd%",date.dayOfMonth.toString().padStart(2,'0'))
        fun status(context: Context,status: CountdownStatus): String {
            val (key,res)=when(status) { CountdownStatus.DONE -> "passed" to R.string.ez_passed;CountdownStatus.TODAY -> "today" to R.string.status_today;else -> "left" to R.string.ez_left }
            return TextPool(context).get(key).ifEmpty { context.getString(res) }
        }
    }
}
