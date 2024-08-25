package com.datecs.lineagoogle.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.datecs.lineagoogle.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implements log view
 */
@Suppress("MemberVisibilityCanBePrivate", "Unused")
class LogView : LinearLayout {
    private lateinit var scrollView: ScrollView
    private lateinit var textView: TextView
    private var maxLogSize = 0
    private var showTime = false

    private companion object {
        private const val MAX_LOG_SIZE = 4 * 1024

        private val simpleDateFormat = SimpleDateFormat("ss.SSS", Locale.US)
    }

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
        LayoutInflater.from(context).inflate(R.layout.log, this)
        scrollView = findViewById<View>(R.id.log_scroll) as ScrollView
        textView = findViewById<View>(R.id.log_text) as TextView
        maxLogSize = MAX_LOG_SIZE
        showTime = true
    }

    private fun trimLog() {
        val editable = textView.editableText
        if (editable != null && editable.length > maxLogSize) {
            editable.delete(0, editable.length - maxLogSize)
        }
    }

    fun setLogSize(size: Int) {
        maxLogSize = size
        trimLog()
    }

    fun showTime(on: Boolean) {
        showTime = on
    }

    fun clear() {
        textView.text = ""
    }

    fun add(text: String, color: Int, bold: Boolean) {
        var s = if (showTime) {
                    simpleDateFormat.format(Date()) + " " + text
        } else {
            text
        }
        s += "\n"
        val start = textView.getText().length
        textView.append(s)
        val end = textView.getText().length
        val spannableText = textView.getText() as Spannable
        spannableText.setSpan(ForegroundColorSpan(color), start, end, 0)
        if (bold) {
            spannableText.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
        }
        trimLog()
        scrollView.post { scrollView.fullScroll(FOCUS_DOWN) }
    }

    fun addD(text: String) {
        add(text, Color.DKGRAY, false)
    }

    fun addV(text: String) {
        add(text, Color.LTGRAY, false)
    }

    fun addE(text: String) {
        add(text, Color.RED, false)
    }

    fun addI(text: String) {
        add(text, Color.parseColor("#006400"), false)
    }

    fun addW(text: String) {
        add(text, Color.parseColor("#FF6600"), false)
    }

    fun add(text: String) {
        if (text.startsWith("<E>")) {
            addE(text.substring(3))
        } else if (text.startsWith("<W>")) {
            addW(text.substring(3))
        } else if (text.startsWith("<I>")) {
            addI(text.substring(3))
        } else if (text.startsWith("<D>")) {
            addD(text.substring(3))
        } else if (text.startsWith("<V>")) {
            addV(text.substring(3))
        } else {
            addD(text)
        }
    }
}