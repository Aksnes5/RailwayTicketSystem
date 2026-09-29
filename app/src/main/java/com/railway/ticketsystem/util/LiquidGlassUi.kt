package com.railway.ticketsystem.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Shared programmatic components for secondary pages that are built at runtime. */
object LiquidGlassUi {
    fun card(context: Context, emphasized: Boolean = false): MaterialCardView = MaterialCardView(context).apply {
        radius = context.dp(if (emphasized) 28 else 24).toFloat()
        cardElevation = 0f
        strokeWidth = context.dp(1)
        strokeColor = Color.parseColor(if (emphasized) "#B9DEFF" else "#D7EAFB")
        setCardBackgroundColor(Color.argb(if (emphasized) 246 else 238, 255, 255, 255))
        preventCornerOverlap = false
        useCompatPadding = false
    }

    fun actionButton(context: Context, text: String, primary: Boolean = false): MaterialButton = MaterialButton(context).apply {
        this.text = text
        textSize = 15f
        minHeight = context.dp(48)
        insetTop = 0
        insetBottom = 0
        cornerRadius = context.dp(18)
        isAllCaps = false
        if (primary) {
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1677FF"))
            setTextColor(Color.WHITE)
            strokeWidth = 0
        } else {
            backgroundTintList = ColorStateList.valueOf(Color.argb(236, 245, 251, 255))
            setTextColor(Color.parseColor("#0A64C9"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#C7E4FF"))
            strokeWidth = context.dp(1)
        }
        applyIosPressScale()
    }

    /** Consistent iOS-style selectable chip for filters, seats and service options. */
    fun selectionChip(context: Context, text: String, selected: Boolean = false): MaterialButton = actionButton(context, text).apply {
        minHeight = context.dp(42)
        cornerRadius = context.dp(16)
        textSize = 14f
        if (selected) {
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1677FF"))
            setTextColor(Color.WHITE)
            strokeWidth = 0
        }
    }

    /** Glass list row shared by runtime-built settings, passenger and service pages. */
    fun listRow(
        context: Context,
        title: String,
        detail: String? = null,
        emphasized: Boolean = false
    ): MaterialCardView = card(context, emphasized).apply {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(18), context.dp(14), context.dp(18), context.dp(14))
        }
        content.addView(TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#15253A"))
        })
        detail?.takeIf { it.isNotBlank() }?.let { subtitle ->
            content.addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#60758C"))
                setPadding(0, context.dp(5), 0, 0)
            })
        }
        addView(content)
        applyIosPressScale()
    }

    /** Applies the same non-rectangular press feedback to arbitrary clickable controls. */
    fun applyLiquidTap(target: View): View {
        target.applyIosPressScale()
        return target
    }

    fun capsule(context: Context, text: String, blue: Boolean = true): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor(if (blue) "#0969DA" else "#4F6B85"))
        gravity = Gravity.CENTER
        val vertical = context.dp(5)
        setPadding(context.dp(10), vertical, context.dp(10), vertical)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(14).toFloat()
            setColor(Color.parseColor(if (blue) "#E7F3FF" else "#F1F7FC"))
            setStroke(context.dp(1), Color.parseColor(if (blue) "#C7E4FF" else "#D6E5F0"))
        }
    }

    fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
