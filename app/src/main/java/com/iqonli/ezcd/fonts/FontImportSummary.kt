// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import com.iqonli.ezcd.R

internal object FontImportSummary {
    fun text(context: Context, report: FontImportReport): String = listOf(
        report.added to R.string.ez_font_import_success_count,
        report.duplicates to R.string.ez_font_import_duplicate_count,
        report.failures to R.string.ez_font_import_failed_count,
    ).filter { it.first > 0 }.joinToString(context.getString(R.string.ez_font_import_separator)) { (count, resource) -> context.getString(resource, count) }
}
