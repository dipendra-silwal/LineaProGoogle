package com.datecs.lineagoogle.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.datecs.lineagoogle.R

/**
 * Implements status view
 */
class StatusView : RelativeLayout {
    private lateinit var imageView: ImageView

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
        LayoutInflater.from(context).inflate(R.layout.status, this)
        imageView = findViewById<View>(R.id.status_image) as ImageView
    }

    fun hide() {
        visibility = GONE
    }

    fun show(resId: Int) {
        imageView.setImageResource(resId)
        visibility = VISIBLE
    }
}