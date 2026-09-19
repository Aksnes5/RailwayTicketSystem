package com.railway.ticketsystem.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** A deliberately simplified station floor plan. It visualises guidance; it is not a survey map. */
class StationGuideMapView(context: Context) : View(context) {
    var target: String = "检票口"
        set(value) { field = value; invalidate() }
    var gate: String = "A1"
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(330f).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); val pad = dp(14f)
        val corner = dp(24f)
        paint.style = Paint.Style.FILL; paint.color = Color.argb(205, 255, 255, 255)
        canvas.drawRoundRect(RectF(pad, pad, w - pad, h - pad), corner, corner, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = Color.rgb(190, 214, 232)
        canvas.drawRoundRect(RectF(pad, pad, w - pad, h - pad), corner, corner, paint)

        val scale = min(w / dp(360f), h / dp(330f)).coerceAtLeast(0.76f)
        fun zone(left: Float, top: Float, right: Float, bottom: Float, label: String, color: Int) {
            val rect = RectF(left * scale, top * scale, right * scale, bottom * scale)
            paint.style = Paint.Style.FILL; paint.color = color
            canvas.drawRoundRect(rect, dp(15f), dp(15f), paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = Color.argb(110, 37, 140, 244)
            canvas.drawRoundRect(rect, dp(15f), dp(15f), paint)
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = dp(12f); paint.color = Color.rgb(24, 34, 48)
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(4f), paint)
        }

        zone(28f, 230f, 118f, 285f, "进站口", Color.rgb(235, 247, 255))
        zone(132f, 185f, 222f, 240f, "安检区", Color.rgb(232, 243, 255))
        zone(110f, 88f, 255f, 165f, "候车区", Color.rgb(226, 241, 255))
        zone(268f, 88f, 342f, 143f, "$gate 检票口", Color.rgb(214, 237, 255))
        zone(265f, 170f, 342f, 225f, "餐饮区", Color.rgb(244, 249, 255))
        zone(28f, 150f, 98f, 205f, "无障碍电梯", Color.rgb(244, 249, 255))
        zone(28f, 296f, 170f, 322f, "停车 / 上客区", Color.rgb(245, 249, 252))

        val start = point(73f, 230f, scale)
        val security = point(177f, 240f, scale)
        val waiting = point(183f, 165f, scale)
        val end = when (target) {
            "餐饮区" -> point(303f, 225f, scale)
            "无障碍通道" -> point(63f, 205f, scale)
            "停车场" -> point(100f, 296f, scale)
            "候车区" -> waiting
            else -> point(305f, 143f, scale)
        }
        val path = Path().apply {
            moveTo(start.first, start.second)
            lineTo(security.first, security.second)
            lineTo(waiting.first, waiting.second)
            lineTo(end.first, end.second)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(4f); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = Color.rgb(37, 140, 244)
        canvas.drawPath(path, paint)
        listOf(start, security, waiting, end).distinct().forEach { point ->
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(point.first, point.second, dp(6f), paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f); paint.color = Color.rgb(37, 140, 244); canvas.drawCircle(point.first, point.second, dp(6f), paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = dp(13f); paint.color = Color.rgb(10, 90, 168)
        canvas.drawText("前往$target", w / 2f, dp(47f), paint)
    }

    private fun point(x: Float, y: Float, scale: Float) = x * scale to y * scale
}
