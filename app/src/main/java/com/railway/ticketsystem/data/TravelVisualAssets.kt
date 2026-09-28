package com.railway.ticketsystem.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.railway.ticketsystem.R

/** Maps stable local catalog identifiers to lightweight, bundled visual assets. */
object TravelVisualAssets {
    private data class MealVisual(
        val resource: Int,
        val column: Int = 0,
        val row: Int = 0,
        val columns: Int = 1,
        val rows: Int = 1
    )

    private val mealTileCache = mutableMapOf<String, Bitmap>()

    private fun mealVisual(productId: String): MealVisual = when (productId) {
        "meal_15", "meal_15_huimin" -> MealVisual(R.drawable.meal_15)
        "meal_30" -> MealVisual(R.drawable.meal_30)
        "meal_45" -> MealVisual(R.drawable.meal_45)
        "meal_55" -> MealVisual(R.drawable.meal_55)
        "drink_nongfu" -> MealVisual(R.drawable.drink_nongfu)
        "drink_baisui" -> MealVisual(R.drawable.drink_baisui)
        "drink_tea" -> MealVisual(R.drawable.drink_tea)
        "drink_yuanqi" -> MealVisual(R.drawable.drink_yuanqi)
        "drink_cola" -> MealVisual(R.drawable.drink_cola)
        "snack_lays" -> MealVisual(R.drawable.snack_lays)
        "snack_oreo" -> MealVisual(R.drawable.snack_oreo)
        "snack_zhouheiya" -> MealVisual(R.drawable.snack_zhouheiya)
        "snack_nuts" -> MealVisual(R.drawable.snack_nuts)

        // Regional hot meals: Wuhan, Beijing, Guangzhou / Chengdu, Shanghai, pork rice.
        "meal_wuhan_01" -> MealVisual(R.drawable.meal_regional_grid, 0, 0, 3, 2)
        "meal_beijing_01" -> MealVisual(R.drawable.meal_regional_grid, 1, 0, 3, 2)
        "meal_guangzhou_01" -> MealVisual(R.drawable.meal_regional_grid, 2, 0, 3, 2)
        "meal_chengdu_01" -> MealVisual(R.drawable.meal_regional_grid, 0, 1, 3, 2)
        "meal_shanghai_01" -> MealVisual(R.drawable.meal_regional_grid, 1, 1, 3, 2)
        "meal_15_pork" -> MealVisual(R.drawable.meal_regional_grid, 2, 1, 3, 2)

        // Station takeaway: fast food, chicken soup, tea drinks / steamed meal, coffee, fruit.
        "kfc_hankou_01" -> MealVisual(R.drawable.meal_station_takeaway_grid, 0, 0, 3, 2)
        "laoxiangji_nanjing_01" -> MealVisual(R.drawable.meal_station_takeaway_grid, 1, 0, 3, 2)
        "chayan_changsha_01" -> MealVisual(R.drawable.meal_station_takeaway_grid, 2, 0, 3, 2)
        "zhenggongfu_guangzhou_01" -> MealVisual(R.drawable.meal_station_takeaway_grid, 0, 1, 3, 2)
        "starbucks_zhengzhou_01", "drink_coffee_01" -> MealVisual(R.drawable.meal_station_takeaway_grid, 1, 1, 3, 2)
        "fruit_cup_01" -> MealVisual(R.drawable.meal_station_takeaway_grid, 2, 1, 3, 2)

        // Travel specialties: duck neck, roast duck / salt duck, mahua.
        "spec_zhouheiya_box" -> MealVisual(R.drawable.meal_specialty_grid, 0, 0, 2, 2)
        "spec_quanjude_roastduck" -> MealVisual(R.drawable.meal_specialty_grid, 1, 0, 2, 2)
        "spec_nanjing_saltduck" -> MealVisual(R.drawable.meal_specialty_grid, 0, 1, 2, 2)
        "spec_tianjin_mahua" -> MealVisual(R.drawable.meal_specialty_grid, 1, 1, 2, 2)

        else -> when {
            productId.contains("coffee") || productId.contains("starbucks") -> MealVisual(R.drawable.meal_station_takeaway_grid, 1, 1, 3, 2)
            productId.contains("fruit") -> MealVisual(R.drawable.meal_station_takeaway_grid, 2, 1, 3, 2)
            productId.contains("tea") || productId.contains("chayan") || productId.contains("drink") -> MealVisual(R.drawable.drink_tea)
            productId.contains("zhouheiya") || productId.contains("duck") || productId.contains("spec") || productId.contains("snack") -> MealVisual(R.drawable.meal_specialty_grid, 0, 0, 2, 2)
            productId.contains("kfc") -> MealVisual(R.drawable.meal_station_takeaway_grid, 0, 0, 3, 2)
            productId.contains("guangzhou") -> MealVisual(R.drawable.meal_regional_grid, 2, 0, 3, 2)
            productId.contains("shanghai") -> MealVisual(R.drawable.meal_regional_grid, 1, 1, 3, 2)
            productId.contains("chengdu") -> MealVisual(R.drawable.meal_regional_grid, 0, 1, 3, 2)
            productId.contains("wuhan") -> MealVisual(R.drawable.meal_regional_grid, 0, 0, 3, 2)
            productId.contains("beijing") || productId.contains("beef") || productId.contains("vip") -> MealVisual(R.drawable.meal_regional_grid, 1, 0, 3, 2)
            else -> MealVisual(R.drawable.meal_15)
        }
    }

    /** Retained for callers that only need the fallback resource identity. */
    fun mealImage(productId: String): Int = mealVisual(productId).resource

    /** Returns the individual product tile when a bundled catalogue visual is a collage. */
    fun mealDrawable(context: Context, productId: String): Drawable? {
        val visual = mealVisual(productId)
        if (visual.columns == 1 && visual.rows == 1) {
            return ContextCompat.getDrawable(context, visual.resource)
        }
        val cacheKey = "${visual.resource}:${visual.column}:${visual.row}:${visual.columns}:${visual.rows}"
        val tile = synchronized(mealTileCache) {
            mealTileCache.getOrPut(cacheKey) { cropTile(context, visual) }
        }
        return BitmapDrawable(context.resources, tile)
    }

    private fun cropTile(context: Context, visual: MealVisual): Bitmap {
        val source = BitmapFactory.decodeResource(context.resources, visual.resource)
            ?: error("Unable to decode meal asset ${visual.resource}")
        val cellWidth = source.width / visual.columns
        val cellHeight = source.height / visual.rows
        val left = (visual.column * cellWidth).coerceAtMost(source.width - cellWidth)
        val top = (visual.row * cellHeight).coerceAtMost(source.height - cellHeight)
        return Bitmap.createBitmap(source, left, top, cellWidth, cellHeight)
    }

    /** Every sellable hotel room maps to the matching bed and space configuration. */
    fun roomImage(room: HotelRoom): Int = when {
        room.name.contains("家庭") -> R.drawable.hotel_room_family
        room.name.contains("套房") -> R.drawable.hotel_room_suite
        room.name.contains("精选") || room.name.contains("几木") ||
            room.amenities.contains("浴缸") || room.amenities.contains("咖啡机") -> R.drawable.hotel_room_premium
        room.name.contains("双床") || room.name.contains("标准房") -> R.drawable.hotel_room_twin
        else -> R.drawable.hotel_room_queen
    }
}
