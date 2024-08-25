package com.datecs.lineagoogle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.datecs.linea.LineaPro
import com.datecs.lineagoogle.connectivity.UsbDeviceConnector
import java.io.IOException

private const val TAG = "LineaManager"

class LineaManager(private val mContext: Context) {
    private var connectionListener: LineaConnection? = null
    private var connector: UsbDeviceConnector? = null

    var lineaPro: LineaPro? = null
        private set

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        val usbDetachReceiver: BroadcastReceiver = UsbDetachedReceiver()
        val usbDetachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        mContext.registerReceiver(usbDetachReceiver, usbDetachFilter)
    }

    fun setConnectionListener(connectionListener: LineaConnection?) {
        this.connectionListener = connectionListener
    }

    private fun raiseConnectionStateChanged(connected: Boolean) {
        if (connectionListener != null) {
            if (connected) {
                connectionListener!!.onLineaConnected(lineaPro!!)
            } else {
                connectionListener!!.onLineaDisconnected()
            }
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect()")
        if (connector != null) {
            try {
                connector!!.close()
            } catch (ignored: Exception) {
            }
            connector = null
        }
        if (lineaPro != null) {
            try {
                lineaPro!!.close()
            } catch (ignored: IOException) {
            }
            lineaPro = null
        }
        raiseConnectionStateChanged(false)
    }

    fun connect() {
        val manager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = manager.getDeviceList()

        for (device in deviceList.values) {
            if (manager.hasPermission(device)) {
                val connector = UsbDeviceConnector(mContext, manager, device)
                try {
                    connector.connect()
                    this.connector = connector
                    lineaPro = LineaPro(connector.getInputStream(), connector.getOutputStream())
                    raiseConnectionStateChanged(true)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to connect", e)
                    return
                }
            }
        }
    }

    private inner class UsbDetachedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            Log.w(TAG, "Disconnect $device")
            val connector = connector
            if (connector != null && connector.device == device) {
                disconnect()
            }
        }
    }
}
