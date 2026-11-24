package com.litephone.launcher

import android.app.Application

class LiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ui.ACCENT = Look.accent(this)
        // Texted unlock codes were removed; clear anything an earlier version saved.
        Prefs.get(this).edit()
            .remove("unlock_codes").remove("unlock_codes_salt").remove("unlock_code_minutes")
            .remove("holder_name").remove("holder_phone")
            .apply()
    }
}
