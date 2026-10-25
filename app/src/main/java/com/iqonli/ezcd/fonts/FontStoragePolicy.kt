// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import com.iqonli.ezcd.R

internal object FontStoragePolicy {
    fun confirmation(from: FontStorageMode, to: FontStorageMode): Int = when (from to to) {
        FontStorageMode.DIRECTORY to FontStorageMode.INTERNAL -> R.string.ez_font_directory_to_internal
        FontStorageMode.INTERNAL to FontStorageMode.DIRECTORY -> R.string.ez_font_internal_to_directory
        FontStorageMode.DIRECTORY to FontStorageMode.REFERENCE -> R.string.ez_font_directory_to_reference
        FontStorageMode.REFERENCE to FontStorageMode.DIRECTORY -> R.string.ez_font_reference_to_directory
        FontStorageMode.INTERNAL to FontStorageMode.REFERENCE -> R.string.ez_font_internal_to_reference
        FontStorageMode.REFERENCE to FontStorageMode.INTERNAL -> R.string.ez_font_reference_to_internal
        else -> R.string.ez_font_internal_to_directory
    }
}
