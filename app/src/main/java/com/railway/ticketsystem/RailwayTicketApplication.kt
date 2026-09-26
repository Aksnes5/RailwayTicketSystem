package com.railway.ticketsystem

import android.app.Application
import com.railway.ticketsystem.data.AccessibilityPreferences

class RailwayTicketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must happen before the first Activity inflates so DayNight resources are selected globally.
        AccessibilityPreferences.applyNightMode(this)
        com.railway.ticketsystem.data.AppRepositoryProvider.initialize(this)
        com.railway.ticketsystem.data.DataSourceModePreferences.init(this)
    }
}