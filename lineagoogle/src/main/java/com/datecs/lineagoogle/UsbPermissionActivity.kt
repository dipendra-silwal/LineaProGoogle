package com.datecs.lineagoogle

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity

class UsbPermissionActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        val app = applicationContext as MyApplication
        app.lineaManager.connect()
        finish()
    }
}