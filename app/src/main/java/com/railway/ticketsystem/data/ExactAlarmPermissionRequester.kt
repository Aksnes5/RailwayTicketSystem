package com.railway.ticketsystem.data

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * Android 12+ treats exact alarms as a special permission: it cannot be requested with
 * `requestPermissions`, the user has to flip it on a system settings page.  Without it the
 * scheduler still fires, just on an inexact schedule.
 */
object ExactAlarmPermissionRequester {

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return manager.canScheduleExactAlarms()
    }

    /** Returns true when exact alarms are already allowed, otherwise opens the grant screen. */
    fun requestIfNeeded(activity: Activity): Boolean {
        if (isGranted(activity)) return true
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${activity.packageName}")
        )
        // Some OEM builds ship without this settings page; the inexact fallback still works.
        val launched = runCatching { activity.startActivity(intent) }.isSuccess
        if (!launched) {
            Toast.makeText(activity, "请在系统设置中允许本应用使用精确闹钟", Toast.LENGTH_LONG).show()
        }
        return false
    }
}
