// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

object InsetUtils {
    fun applySystemBarPadding(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom

        view.setOnApplyWindowInsetsListener { target, insets ->
            val safeInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val modern = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime(),
                )
                SafeInsets(modern.left, modern.top, modern.right, modern.bottom)
            } else {
                legacySafeInsets(insets)
            }

            target.setPadding(
                baseLeft + safeInsets.left,
                baseTop + safeInsets.top,
                baseRight + safeInsets.right,
                baseBottom + safeInsets.bottom,
            )
            insets
        }
        view.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun legacySafeInsets(insets: WindowInsets): SafeInsets {
        var left = insets.systemWindowInsetLeft
        var top = insets.systemWindowInsetTop
        var right = insets.systemWindowInsetRight
        var bottom = insets.systemWindowInsetBottom

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            insets.displayCutout?.let { cutout ->
                left = max(left, cutout.safeInsetLeft)
                top = max(top, cutout.safeInsetTop)
                right = max(right, cutout.safeInsetRight)
                bottom = max(bottom, cutout.safeInsetBottom)
            }
        }

        return SafeInsets(left, top, right, bottom)
    }

    private data class SafeInsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
