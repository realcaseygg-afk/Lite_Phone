package com.litephone.launcher

import android.app.Application

class LiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ui.ACCENT = Look.accent(this)
    }
}
