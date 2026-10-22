package com.josercha.openplayer

import android.app.Application

class OpenPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyMode(this)
    }
}
