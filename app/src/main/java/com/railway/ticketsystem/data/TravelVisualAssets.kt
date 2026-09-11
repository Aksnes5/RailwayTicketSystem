package com.railway.ticketsystem.data

import com.railway.ticketsystem.R

/** Maps stable local catalog identifiers to lightweight, bundled visual assets. */
object TravelVisualAssets {
    fun mealImage(productId: String): Int = when (productId) {
        "meal_15" -> R.drawable.meal_15
        "meal_30" -> R.drawable.meal_30
        "meal_45" -> R.drawable.meal_45
        "meal_55" -> R.drawable.meal_55
        "drink_nongfu" -> R.drawable.drink_nongfu
        "drink_baisui" -> R.drawable.drink_baisui
        "drink_tea" -> R.drawable.drink_tea
        "drink_yuanqi" -> R.drawable.drink_yuanqi
        "drink_cola" -> R.drawable.drink_cola
        "snack_lays" -> R.drawable.snack_lays
        "snack_oreo" -> R.drawable.snack_oreo
        "snack_zhouheiya" -> R.drawable.snack_zhouheiya
        "snack_nuts" -> R.drawable.snack_nuts
        else -> R.drawable.meal_15
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
