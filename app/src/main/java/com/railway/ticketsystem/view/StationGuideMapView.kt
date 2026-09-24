package com.railway.ticketsystem.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Lightweight, density-safe station floor-plan rendering for ticket-linked walking guidance. */
class StationGuideMapView(context: Context) : View(context) {
    var target: String = "检票口"
        set(value) { field = value; invalidate() }
    var gate: String = "A1"
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    init {
        minimumHeight = dp(330f).toInt()
        contentDescription = "站内服务导览图"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(330f).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        val padding = dp(14f)
        val contentWidth = (widthPx - padding * 2f).coerceAtLeast(1f)
        val contentHeight = (heightPx - padding * 2f).coerceAtLeast(1f)
        // Base geometry is authored in dp. Convert both axes to physical pixels before scaling;
        // otherwise the diagram collapses into the top-left on xxhdpi / xxxhdpi screens.
        val scale = min(contentWidth / dp(360f), contentHeight / dp(330f)).coerceAtLeast(0.1f)
        fun x(value: Float) = padding + dp(value) * scale
        fun y(value: Float) = padding + dp(value) * scale
        fun point(rawX: Float, rawY: Float) = x(rawX) to y(rawY)
        val radius = dp(18f) * scale

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(205, 255, 255, 255)
        canvas.drawRoundRect(RectF(padding, padding, widthPx - padding, heightPx - padding), dp(24f), dp(24f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.rgb(190, 214, 232)
        canvas.drawRoundRect(RectF(padding, padding, widthPx - padding, heightPx - padding), dp(24f), dp(24f), paint)

        fun zone(left: Float, top: Float, right: Float, bottom: Float, label: String, color: Int) {
            val rect = RectF(x(left), y(top), x(right), y(bottom))
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = Color.argb(110, 37, 140, 244)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (dp(12f) * scale).coerceAtLeast(dp(10f))
            paint.color = Color.rgb(24, 34, 48)
            canvas.drawText(label, rect.centerX(), rect.centerY() + (dp(4f) * scale), paint)
        }

        zone(28f, 230f, 118f, 285f, "进站口", Color.rgb(235, 247, 255))
        zone(132f, 185f, 222f, 240f, "安检区", Color.rgb(232, 243, 255))
        zone(110f, 88f, 255f, 165f, "候车区", Color.rgb(226, 241, 255))
        zone(268f, 88f, 342f, 143f, "$gate 检票口", Color.rgb(214, 237, 255))
        zone(265f, 170f, 342f, 225f, "餐饮区", Color.rgb(244, 249, 255))
        zone(28f, 150f, 98f, 205f, "无障碍电梯", Color.rgb(244, 249, 255))
        zone(28f, 296f, 170f, 322f, "停车 / 上客区", Color.rgb(245, 249, 252))

        val entrance = point(73f, 258f)
        val security = point(177f, 213f)
        val waiting = point(183f, 165f)
        val destination = when (target) {
            "餐饮区", "餐饮零售" -> point(303f, 198f)
            "无障碍通道", "无障碍电梯" -> point(63f, 178f)
            "停车场" -> point(100f, 309f)
            "候车区", "贵宾候车厅" -> waiting
            else -> point(305f, 116f)
        }
        val path = Path().apply {
            moveTo(entrance.first, entrance.second)
            lineTo(security.first, security.second)
            lineTo(waiting.first, waiting.second)
            lineTo(destination.first, destination.second)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (dp(4f) * scale).coerceAtLeast(dp(3f))
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.rgb(37, 140, 244)
        canvas.drawPath(path, paint)
        listOf(entrance, security, waiting, destination).distinct().forEach { marker ->
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(marker.first, marker.second, (dp(6f) * scale).coerceAtLeast(dp(4f)), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (dp(3f) * scale).coerceAtLeast(dp(2f))
            paint.color = Color.rgb(37, 140, 244)
            canvas.drawCircle(marker.first, marker.second, (dp(6f) * scale).coerceAtLeast(dp(4f)), paint)
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = (dp(13f) * scale).coerceAtLeast(dp(11f))
        paint.color = Color.rgb(10, 90, 168)
        canvas.drawText("前往$target", widthPx / 2f, y(28f), paint)
    }
}