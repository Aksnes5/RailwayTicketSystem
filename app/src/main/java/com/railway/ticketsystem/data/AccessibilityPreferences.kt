package com.railway.ticketsystem.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView

/** Shared preference layer for readable type, night appearance and low-jank rendering. */
object AccessibilityPreferences {
    private const val PREFS = "accessibility_preferences"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_FONT = "font_scale"
    private const val KEY_PERFORMANCE = "performance_mode"

    fun isDark(context: Context): Boolean = prefs(context).getBoolean(KEY_DARK, false)
    fun fontScale(context: Context): Float = prefs(context).getFloat(KEY_FONT, 1f).coerceIn(0.9f, 1.35f)
    fun performanceMode(context: Context): Boolean = prefs(context).getBoolean(KEY_PERFORMANCE, true)

    fun setDark(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK, enabled).apply()
        applyNightMode(context)
    }
    fun setFontScale(context: Context, scale: Float) = prefs(context).edit().putFloat(KEY_FONT, scale.coerceIn(0.9f, 1.35f)).apply()
    fun setPerformanceMode(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_PERFORMANCE, enabled).apply()

    fun applyNightMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(context)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = fontScale(context)
        return context.createConfigurationContext(configuration)
    }

    fun applySystemBarAppearance(activity: Activity) {
        val dark = isDark(activity)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    /** Adds TalkBack labels where a custom visual control omitted one and removes costly list animators. */
    fun applyToContent(root: View, context: Context) {
        val performance = performanceMode(context)
        fun visit(view: View) {
            if (view is TextView && view.isClickable && view.contentDescription.isNullOrBlank() && view.text.isNotBlank()) {
                view.contentDescription = view.text
            }
            if (view is RecyclerView && performance) {
                view.setHasFixedSize(true)
                view.itemAnimator = null
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}