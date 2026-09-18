package com.railway.ticketsystem.activity

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.railway.ticketsystem.data.AccessibilityPreferences
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Shared edge-to-edge treatment for every regular screen.
 *
 * The root itself draws the common liquid backdrop behind the system bars; only its
 * content is inset. That keeps the clock and gesture area visually part of the page
 * without letting toolbars or lists slide underneath them.
 */
open class ImmersiveActivity : AccessibleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        AccessibilityPreferences.applySystemBarAppearance(this)

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) ?: return
        val initialLeft = contentRoot.paddingLeft
        val initialTop = contentRoot.paddingTop
        val initialRight = contentRoot.paddingRight
        val initialBottom = contentRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(contentRoot)
    }
}
