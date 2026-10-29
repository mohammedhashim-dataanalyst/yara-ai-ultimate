package com.yara.ai.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Perm {
    fun hasMic(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Runtime permissions Yara needs for voice + the background notification. */
    fun missing(c: Context): Array<String> {
        val m = ArrayList<String>()
        if (!hasMic(c)) m.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            m.add(Manifest.permission.POST_NOTIFICATIONS)
        return m.toTypedArray()
    }

    fun startWake(c: Context) {
        if (!hasMic(c)) return
        c.startForegroundService(Intent(c, YaraService::class.java))
    }

    fun stopWake(c: Context) {
        c.stopService(Intent(c, YaraService::class.java))
        Prefs(c).wakeEnabled = false
    }
}
