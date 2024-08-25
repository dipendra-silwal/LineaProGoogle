package com.datecs.lineagoogle

import android.app.Application

class MyApplication : Application() {

    lateinit var lineaManager: LineaManager
        private set

    override fun onCreate() {
        super.onCreate()
        lineaManager = LineaManager(this)
    }
}
