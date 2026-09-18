package com.railway.ticketsystem.activity

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.data.AccessibilityPreferences

/** Base for the few legacy screens that manage their own insets but still need accessibility prefs. */
open class AccessibleActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AccessibilityPreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityPreferences.applyNightMode(this)
        super.onCreate(savedInstanceState)
        AccessibilityPreferences.applySystemBarAppearance(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        (findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0))?.let {
            AccessibilityPreferences.applyToContent(it, this)
        }
    }
}