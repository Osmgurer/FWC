package com.focuswinecellars.kiosk.kiosk

import android.app.ActivityManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class KioskController(
    private val activity: ComponentActivity
) {
    fun enterKioskMode() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        )
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (isLockTaskPermitted()) {
            activity.startLockTask()
        }
    }

    fun exitKioskMode() {
        if (isLockTaskPermitted()) {
            activity.stopLockTask()
        }
    }

    private fun isLockTaskPermitted(): Boolean {
        val activityManager =
            activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.isLockTaskPermitted(activity.packageName)
    }
}
