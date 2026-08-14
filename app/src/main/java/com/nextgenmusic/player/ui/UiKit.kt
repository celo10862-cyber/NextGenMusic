package com.nextgenmusic.player.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.nextgenmusic.player.R

object UiKit {
    fun page(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 24))
        setBackgroundColor(Color.rgb(7, 10, 18))
    }

    fun scroll(context: Context): ScrollView = ScrollView(context).apply {
        isFillViewport = true
        setBackgroundColor(Color.rgb(7, 10, 18))
    }

    fun title(context: Context, text: String, size: Float = 28f): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.rgb(241, 245, 255))
        textSize = size
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(context, 10))
    }

    fun subtitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.rgb(147, 162, 194))
        textSize = 14f
        setPadding(0, 0, 0, dp(context, 12))
    }

    fun section(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text.uppercase()
        setTextColor(Color.rgb(86, 231, 255))
        textSize = 12f
        letterSpacing = .14f
        setPadding(0, dp(context, 20), 0, dp(context, 8))
    }

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 18).toFloat()
            setColor(Color.rgb(16, 21, 37))
            setStroke(dp(context, 1), Color.rgb(32, 46, 73))
        }
    }

    fun button(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.rgb(7, 10, 18))
        textSize = 14f
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(Color.rgb(86, 231, 255))
        }
        setOnClickListener { onClick() }
        minimumHeight = dp(context, 48)
    }

    fun outlineButton(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.rgb(86, 231, 255))
        textSize = 14f
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 1), Color.rgb(86, 231, 255))
        }
        setOnClickListener { onClick() }
        minimumHeight = dp(context, 48)
    }

    fun input(context: Context, hint: String): EditText = EditText(context).apply {
        this.hint = hint
        setHintTextColor(Color.rgb(147, 162, 194))
        setTextColor(Color.rgb(241, 245, 255))
        textSize = 16f
        singleLine = true
        setPadding(dp(context, 14), 0, dp(context, 14), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(Color.rgb(16, 21, 37))
            setStroke(dp(context, 1), Color.rgb(32, 46, 73))
        }
        minimumHeight = dp(context, 52)
    }

    fun progress(context: Context): ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(86, 231, 255))
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(32, 46, 73))
    }

    fun label(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.rgb(241, 245, 255))
        textSize = 16f
    }

    fun spacer(context: Context, height: Int = 10): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, height))
    }

    fun row(context: Context, vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}