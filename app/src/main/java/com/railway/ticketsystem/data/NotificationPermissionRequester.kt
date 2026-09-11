package com.railway.ticketsystem.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Requests Android 13+ notification access once, before the first payment or travel alert. */
object NotificationPermissionRequester {
    private const val PREFS_NAME = "notification_permission_prompt"
    private const val PROMPTED_KEY = "prompted"
    const val REQUEST_CODE = 302

    fun requestOnFirstUse(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) return true

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PROMPTED_KEY, false)) {
            prefs.edit().putBoolean(PROMPTED_KEY, true).apply()
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE
            )
        }
        return false
    }
}
