package com.railway.ticketsystem.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 全局沉浸式 Edge-to-Edge 统一规范与键盘避让工具
 */
object EdgeToEdgeHelper {

    fun applyEdgeToEdge(
        activity: Activity,
        targetContent: View? = null,
        handleIme: Boolean = true
    ) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val contentView = targetContent ?: activity.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
            ?: return

        val initialLeft = contentView.paddingLeft
        val initialTop = contentView.paddingTop
        val initialRight = contentView.paddingRight
        val initialBottom = contentView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = if (handleIme) insets.getInsets(WindowInsetsCompat.Type.ime()) else androidx.core.graphics.Insets.NONE

            val bottomInset = if (ime.bottom > systemBars.bottom) {
                ime.bottom
            } else {
                systemBars.bottom
            }

            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + bottomInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }
}
