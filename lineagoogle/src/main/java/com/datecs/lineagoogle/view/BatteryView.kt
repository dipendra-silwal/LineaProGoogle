package com.datecs.lineagoogle.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.datecs.linea.LineaPro
import com.datecs.lineagoogle.R

/**
 * Implements a battery view control.
 */
class BatteryView : LinearLayout {
    private lateinit var batteryView: ImageView
    private lateinit var stateView: ImageView
    private lateinit var textView: TextView

    constructor(context: Context) : super(context) {
        initViews(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initViews(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initViews(context)
    }

    private fun initViews(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.battery, this)
        batteryView = findViewById<View>(R.id.battery_percent) as ImageView
        stateView = findViewById<View>(R.id.battery_state) as ImageView
        textView = findViewById<View>(R.id.battery_text) as TextView
        update(null)
    }

    fun update(batteryInfo: LineaPro.BatteryInfo?) {
        if (batteryInfo == null) {
            batteryView.setImageResource(R.drawable.battery3)
            stateView.setImageResource(R.drawable.question)
            textView.text = ""
        } else {
            val fuelGauge = batteryInfo.fuelgauge
            if (fuelGauge != null) {
                textView.setTextColor(Color.WHITE)
            } else {
                textView.setTextColor(Color.parseColor("#FFA600"))
            }
            val voltage = batteryInfo.voltage
            val initialCapacity = batteryInfo.initialCapacity
            val capacity = batteryInfo.capacity
            val healthLevel = batteryInfo.healthLevel
            val isCharging = batteryInfo.isCharging
            if (isCharging) {
                stateView.setImageResource(R.drawable.flash)
            } else {
                stateView.setImageDrawable(null)
            }
            if (capacity <= 15) {
                batteryView.setImageResource(R.drawable.battery1)
            } else if (capacity <= 30) {
                batteryView.setImageResource(R.drawable.battery2)
            } else if (capacity <= 45) {
                batteryView.setImageResource(R.drawable.battery3)
            } else if (capacity <= 60) {
                batteryView.setImageResource(R.drawable.battery4)
            } else if (capacity <= 75) {
                batteryView.setImageResource(R.drawable.battery5)
            } else {
                batteryView.setImageResource(R.drawable.battery6)
            }
            val sb = StringBuffer()
            sb.append("" + voltage + "mV\n")
            sb.append("" + initialCapacity + "mAh\n")
            if (fuelGauge != null) {
                val averageCurrent = fuelGauge.averageCurrent
                sb.append("ACC: " + averageCurrent + "mA\n")
            }
            sb.append("SoC: $capacity%\n")
            sb.append("SoH: $healthLevel%")
            textView.text = sb.toString()
        }
    }

    fun reset() {
        update(null)
    }
}
