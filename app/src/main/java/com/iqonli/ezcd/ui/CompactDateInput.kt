// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import com.iqonli.ezcd.countdown.CountdownDateDomain
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal object CompactDateInput {
    private val format = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
    fun parse(text: String): LocalDate? {
        if (text.length != 8 || text.any { it !in '0'..'9' }) return null
        return runCatching { LocalDate.parse(text, format) }.getOrNull()?.takeIf(CountdownDateDomain::contains)
    }
    fun format(date: LocalDate): String = date.format(format)
}
