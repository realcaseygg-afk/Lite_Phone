package com.litephone.launcher

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Being a device admin means Android refuses to uninstall Lite Phone until this is switched off. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "This removes Lite Phone's uninstall protection."

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    }
}
