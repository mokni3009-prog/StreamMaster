package com.streammaster.tv.util

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * CONTRACT §10 — device capability helpers shared by the UI, player and DI
 * layers (low-RAM mode drives the Coil cache cap, the preload window and the
 * theme glows; API helpers gate version-specific features).
 */
object DeviceCaps {

    /** True on low-RAM devices (typically 1–2 GB TV boxes). */
    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice
    }

    fun isAtLeast(api: Int): Boolean = Build.VERSION.SDK_INT >= api

    /** System Picture-in-Picture is available on API 26+. */
    fun hasSystemPip(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Media3 HttpEngine is available on API 34+ (GUIDE B14). */
    fun hasHttpEngine(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** True on Android TV / TV Box devices. */
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
