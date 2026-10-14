package com.example.openplayer

import android.app.Application

class OpenPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyMode(this)
    }
}
