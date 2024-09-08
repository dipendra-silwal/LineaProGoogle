package com.datecs.lineagoogle

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.datecs.linea.LineaPro
import com.datecs.lineagoogle.connectivity.UsbDeviceConnector
import com.datecs.lineagoogle.view.BatteryView
import com.datecs.lineagoogle.view.LogView
import kotlin.math.truncate

import com.datecs.lineagoogle.util.MediaUtil
import java.io.IOException

/**
 *
 */
class MainFragment : Fragment() {
    private lateinit var mVersionView: TextView
    private  lateinit var batteryView: BatteryView
    private lateinit var logView: LogView
    private lateinit var jpegView: ImageView
    @Volatile
    private  var batteryInfo: LineaPro.BatteryInfo? = null

    private var callbacks: LineaAction? = null

    private var bitmap: Bitmap? = null



    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView  = inflater.inflate(R.layout.fragment_main, container, false)
        val handler = Handler(inflater.context.mainLooper)
        mVersionView = rootView.findViewById<View>(R.id.version) as TextView
        //mVersionView.text = BuildInfo.VERSION

        val context = requireContext() // Use context from the fragment
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName

        mVersionView.text = versionName.toString()
        batteryView = rootView.findViewById<View>(R.id.battery) as BatteryView
        batteryView.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            (activity as? MainActivity)?.updateBatteryOnTap()

      //      handler.postDelayed({
                  //QA
//                logView.add("<I>\n\nBattery Information")
//               // val fuelGauge = this.batteryInfo?.fuelgauge
//               val fuelGauge = getBatteryInfo()?.fuelgauge
//                if (fuelGauge != null) {
//                    val tempFahrenheit = truncate(((fuelGauge.temperature.toInt()) - 273.15) *1.8 + 32)
//                    //logView.add("<I>  Temperature: " + fuelGauge.temperature + "K")
//                    logView.add ("<I> Temperature: " + tempFahrenheit.toString() + "F")
//                   // logView.add("<I>  Internal temperature: " + fuelGauge.internalTemperature + "K")
//                    logView.add("<I>  Voltage: " + fuelGauge.voltage + "mV")
//                    logView.add("<I>  Nominal available capacity: " + fuelGauge.nominalAvailableCapacity + "mAh")
//                    logView.add("<I>  Full available capacity: " + fuelGauge.fullAvailableCapacity + "mAh")
//                    logView.add("<I>  Remaining capacity: " + fuelGauge.remainingCapacity + "mAh")
//                    logView.add("<I>  Full charge capacity: " + fuelGauge.fullChargeCapacity + "mAh")
//                    logView.add("<I>  Average current: " + fuelGauge.averageCurrent + "mA" )
//                    logView.add("<I>  Standby current: " + fuelGauge.standbyCurrent + "mA")
//                    logView.add("<I>  Max load current: " + fuelGauge.maxLoadCurrent + "mA")
//                    logView.add("<I>  Average power: " + fuelGauge.averagePower + "mW")
//                    logView.add("<I>  State of charge: " + fuelGauge.stateOfCharge + "%")
//                    logView.add("<I>  State of health: " + fuelGauge.stateOfHealth + "%")
//                } else {
//                    logView.add("<I>  Voltage: " + batteryInfo?.voltage + "mV")
//                    logView.add("<I>  Capacity: " + batteryInfo?.capacity + "%")
//                    logView.add("<I>  Initial capacity: " + batteryInfo?.initialCapacity + "mAh")
//                    logView.add("<I>  State of health: " + batteryInfo?.healthLevel + "%")
//                    logView.add("<W>  Fuel gauge not available")
//                }
//                if (batteryInfo?.isCharging == true) {
//                    logView.add("<I>  Battery is charging")
//                }
//                //updateBattery(batteryInfo)

    //        }, 0)
        }
        logView = rootView.findViewById<View>(R.id.log) as LogView
        logView.showTime(false)
        jpegView = rootView.findViewById(R.id.jpeg)
        rootView.findViewById<View>(R.id.btn_scan).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> callbacks!!.actionStartScan()
                MotionEvent.ACTION_UP -> callbacks!!.actionStopScan()
            }
            false
        }
        rootView.findViewById<View>(R.id.btn_read_tag)
            .setOnClickListener { callbacks!!.actionReadTag() }
        rootView.findViewById<View>(R.id.btn_turn_off).setOnClickListener {
            try {
                callbacks?.actionTurnOff()
            } catch (ex: Exception) {
                Toast.makeText(activity, ex.message, Toast.LENGTH_SHORT).show()
            }
        }
        rootView.findViewById<View>(R.id.btn_settings).setOnClickListener {
            val fragmentManager = getParentFragmentManager()
            val fragmentTransaction = fragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.content, SettingsFragment())
            fragmentTransaction.addToBackStack(null)
            fragmentTransaction.commit()
        }

        rootView.findViewById<View>(R.id.logo).apply{
            var startTime = 0L
            var hasVibrated = false
            var logoPressDuration = 1000L
            // Set a touch listener to determine press duration
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Capture the start time when the user presses down
                        startTime = System.currentTimeMillis()

                        handler.postDelayed({
                            if (!hasVibrated) {
                                // Vibrate the phone
                                MediaUtil.vibrate(context, 700)
                               // Toast.makeText(context, "Vibration Triggered After 1 Second", Toast.LENGTH_SHORT).show()
                                hasVibrated = true  // Set the flag so vibration doesn't trigger again
                            }
                        }, logoPressDuration)  // 1000 milliseconds == 1 second
                    }

                    MotionEvent.ACTION_UP -> {
                        // Capture the time when the user releases the press
                        val endTime = System.currentTimeMillis()
                        // Calculate the press duration
                        val pressDuration = endTime - startTime
                        // Check the press duration and perform actions
                        hasVibrated = false
                        if (pressDuration >= logoPressDuration) {
                            logView.clear()
                            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                            val deviceList = manager.deviceList
                            if(deviceList.size!=0){
                                Toast.makeText(context, "List of connected devices", Toast.LENGTH_SHORT).show()
                                for (device in deviceList.values) {
                                    logView.add("Available Devices: "+device.productName.toString())
                                }
                            }
                            else{
                                Toast.makeText(context, "No devices available", Toast.LENGTH_SHORT).show()
                            }
                            //hasVibrated = true
                            true  // Returning true means the event was handled
                        } else {
                            // Regular short press action
                            logView.clear()
                            Toast.makeText(context, "Reconnecting...", Toast.LENGTH_SHORT).show()
                            hasVibrated = false
                            handler.removeCallbacksAndMessages(null)
                            (activity as? MainActivity)?.reconnect()
                            true
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        // Cancel the scheduled vibration if the touch event is canceled
                        handler.removeCallbacksAndMessages(null)
                    }
                }
                true  // Returning false allows other listeners (e.g., click) to run
            }
        }
        return rootView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = try {
            activity as LineaAction?
        } catch (e: ClassCastException) {
            throw ClassCastException(activity.toString() + " must implement LineaAction")
        }
    }

    fun addLog(text: String?) {
        logView.add(text!!)
    }

    fun clearLog() {
        logView.clear()
    }

    fun resetBattery() {
        batteryView.reset()
    }

    fun updateBattery(batteryInfo: LineaPro.BatteryInfo?) {
        synchronized(this) {
            this.batteryInfo = batteryInfo
            batteryView.update(this.batteryInfo)
            logView.add("<I>\nBattery Information")
            // val fuelGauge = this.batteryInfo?.fuelgauge
            val fuelGauge = batteryInfo?.fuelgauge
            if (fuelGauge != null) {
                val tempFahrenheit = truncate(((fuelGauge.temperature.toInt()) - 273.15) *1.8 + 32)
                //logView.add("<I>  Temperature: " + fuelGauge.temperature + "K")
                logView.add ("<I> Temperature: " + tempFahrenheit.toString() + "F")
                // logView.add("<I>  Internal temperature: " + fuelGauge.internalTemperature + "K")
                logView.add("<I>  Voltage: " + fuelGauge.voltage + "mV")
                logView.add("<I>  Nominal available capacity: " + fuelGauge.nominalAvailableCapacity + "mAh")
                logView.add("<I>  Full available capacity: " + fuelGauge.fullAvailableCapacity + "mAh")
                logView.add("<I>  Remaining capacity: " + fuelGauge.remainingCapacity + "mAh")
                logView.add("<I>  Full charge capacity: " + fuelGauge.fullChargeCapacity + "mAh")
                logView.add("<I>  Average current: " + fuelGauge.averageCurrent + "mA" )
                logView.add("<I>  Standby current: " + fuelGauge.standbyCurrent + "mA")
                logView.add("<I>  Max load current: " + fuelGauge.maxLoadCurrent + "mA")
                logView.add("<I>  Average power: " + fuelGauge.averagePower + "mW")
                logView.add("<I>  State of charge: " + fuelGauge.stateOfCharge + "%")
                logView.add("<I>  State of health: " + fuelGauge.stateOfHealth + "%")
            } else {
                logView.add("<I>  Voltage: " + batteryInfo?.voltage + "mV")
                logView.add("<I>  Capacity: " + batteryInfo?.capacity + "%")
                logView.add("<I>  Initial capacity: " + batteryInfo?.initialCapacity + "mAh")
                logView.add("<I>  State of health: " + batteryInfo?.healthLevel + "%")
                logView.add("<W>  Fuel gauge not available")
            }
            if (batteryInfo?.isCharging == true) {
                logView.add("<I>  Battery is charging")
            }
        }
    }
     fun getBatteryInfo(): LineaPro.BatteryInfo? {
        return synchronized(this) {
            batteryInfo
        }
    }

    fun updateJpeg(bm: Bitmap?) {
        if (bm != null) {
            bitmap?.recycle()
            bitmap = bm
            jpegView.setImageBitmap(bm)
            jpegView.visibility = View.VISIBLE
        } else {
            bitmap = null
            jpegView.setImageBitmap(null)
            jpegView.visibility = View.INVISIBLE
        }
    }
}
