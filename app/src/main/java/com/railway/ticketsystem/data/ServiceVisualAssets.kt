package com.railway.ticketsystem.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.railway.ticketsystem.R

/** Premium, local hero visuals for content-heavy railway service pages. */
object ServiceVisualAssets {
    enum class Hero(val column: Int, val row: Int, val description: String) {
        TOURISM(0, 0, "高铁文旅风景插画"),
        SMART_SPACE(1, 0, "高铁智慧车厢插画"),
        LUGGAGE(2, 0, "高铁行李服务插画"),
        PASSENGER_CARE(0, 1, "重点旅客爱心服务插画"),
        INTERMODAL_TRANSIT(1, 1, "站城接驳服务插画"),
        ENTERPRISE_TRAVEL(2, 1, "企业差旅服务插画")
    }

    private const val COLUMNS = 3
    private const val ROWS = 2
    private val tileCache = mutableMapOf<Hero, Bitmap>()

    fun heroView(context: Context, hero: Hero): ImageView {
        val density = context.resources.displayMetrics.density
        val horizontal = (18 * density).toInt()
        val radius = 24 * density
        return ImageView(context).apply {
            contentDescription = hero.description
            setImageDrawable(heroDrawable(context, hero))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = radius
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.parseColor("#D8E9F8"))
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (152 * density).toInt()
            ).apply {
                setMargins(horizontal, 0, horizontal, (14 * density).toInt())
            }
        }
    }

    fun heroDrawable(context: Context, hero: Hero): Drawable {
        val bitmap = synchronized(tileCache) {
            tileCache.getOrPut(hero) { cropTile(context, hero) }
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun cropTile(context: Context, hero: Hero): Bitmap {
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.service_hero_grid)
            ?: error("Unable to decode service hero visual")
        val cellWidth = source.width / COLUMNS
        val cellHeight = source.height / ROWS
        val left = (hero.column * cellWidth).coerceAtMost(source.width - cellWidth)
        val top = (hero.row * cellHeight).coerceAtMost(source.height - cellHeight)
        return Bitmap.createBitmap(source, left, top, cellWidth, cellHeight)
    }
}
