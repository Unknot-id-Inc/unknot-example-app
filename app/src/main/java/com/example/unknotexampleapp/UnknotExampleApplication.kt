package com.example.unknotexampleapp

import android.app.Application
import org.unknot.android_sdk.util.Logger

class UnknotExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Logger.init(this)
    }
}