package com.railway.ticketsystem.data

import java.io.Serializable
import kotlin.math.absoluteValue

/** A sellable room type belonging to the brand shown on its hotel page. */
data class HotelRoom(
    val name: String,
    val bed: String,
    val area: String,
    val amenities: String,
    val priceOffset: Int,
    val breakfastPrice: Int,
    val cancellation: String
) : Serializable

/** Deterministic local hotel data, used only to present a stable booking journey. */
data class NearbyHotel(
    val brand: String,
    val name: String,
    val distanceMeters: Int,
    val rating: Double,
    val addressHint: String,
    val baseNightlyPrice: Int,
    val rooms: List<HotelRoom>
) : Serializable {
    fun nightlyPrice(room: HotelRoom): Int = baseNightlyPrice + room.priceOffset
}

/**
 * Brand room names follow common real-world product names used by the respective chains.
 * Prices are local reference rates generated per station, not live inventory or live quotes.
 */
object NearbyHotelCatalog {
    private data class Brand(
        val name: String,
        val basePrice: Int,
        val rooms: List<HotelRoom>
    )

    private val brands = listOf(
        Brand("汉庭酒店", 218, listOf(
            room("高级大床房", "1张1.8m大床", "22㎡", "智能电视 · 干湿分离", 0, 25),
            room("双床房", "2张1.2m单人床", "24㎡", "办公桌 · 干湿分离", 35, 25),
            room("家庭房", "1张大床+1张单人床", "28㎡", "亲子空间 · 智能电视", 88, 25)
        )),
        Brand("全季酒店", 338, listOf(
            room("高级大床房", "1张1.8m大床", "26㎡", "全季茶具 · 智能客控", 0, 38),
            room("高级双床房", "2张1.2m单人床", "28㎡", "小爱智能音箱 · 茶具", 45, 38),
            room("套房", "1张1.8m大床", "42㎡", "独立会客区 · 冰箱", 170, 48)
        )),
        Brand("桔子酒店", 358, listOf(
            room("月若流金大床房", "1张1.8m大床", "26㎡", "智能客控 · 冰箱", 0, 42),
            room("月若流金双床房", "2张1.2m单人床", "28㎡", "投屏电视 · 智能客控", 50, 42),
            room("精选大床房", "1张2.0m大床", "32㎡", "浴缸或景观 · 咖啡机", 128, 48)
        )),
        Brand("维也纳国际酒店", 298, listOf(
            room("高级大床房", "1张1.8m大床", "28㎡", "愉梦之床 · 商务书桌", 0, 35),
            room("豪华双床房", "2张1.35m单人床", "32㎡", "落地窗 · 商务书桌", 52, 35),
            room("商务套房", "1张1.8m大床", "48㎡", "会客区 · 浴袍", 162, 45)
        )),
        Brand("如家商旅酒店", 208, listOf(
            room("商旅大床房", "1张1.8m大床", "22㎡", "高速Wi‑Fi · 办公桌", 0, 28),
            room("商旅双床房", "2张1.2m单人床", "25㎡", "办公桌 · 干湿分离", 38, 28),
            room("安心睡大床房", "1张1.8m大床", "24㎡", "静音楼层 · 助眠枕", 70, 32)
        )),
        Brand("亚朵酒店", 398, listOf(
            room("雅致大床房", "1张1.8m大床", "28㎡", "深睡枕 · 投屏电视", 0, 48),
            room("高级大床房", "1张2.0m大床", "32㎡", "深睡床品 · 小冰箱", 72, 48),
            room("几木大床房", "1张2.0m大床", "36㎡", "浴袍 · 迷你吧 · 深睡枕", 158, 58)
        )),
        Brand("麗枫酒店", 278, listOf(
            room("麗人香氛大床房", "1张1.8m大床", "26㎡", "香氛洗护 · 智能客控", 0, 35),
            room("豪华大床房", "1张2.0m大床", "30㎡", "蓝牙音乐 · 香氛洗护", 52, 35),
            room("商务双床房", "2张1.35m单人床", "32㎡", "商务书桌 · 投屏电视", 78, 35)
        )),
        Brand("锦江之星酒店", 198, listOf(
            room("商务大床房A", "1张1.5m大床", "20㎡", "高速Wi‑Fi · 独立卫浴", 0, 22),
            room("商务标准房A", "2张1.2m单人床", "22㎡", "独立卫浴 · 办公桌", 32, 22),
            room("标准房A", "2张1.2m单人床", "20㎡", "高速Wi‑Fi · 独立卫浴", 18, 22)
        )),
        Brand("7天优品酒店", 188, listOf(
            room("优享大床房", "1张1.8m大床", "20㎡", "独立卫浴 · 高速Wi‑Fi", 0, 20),
            room("优享双床房", "2张1.2m单人床", "22㎡", "独立卫浴 · 办公桌", 30, 20),
            room("精选大床房", "1张2.0m大床", "24㎡", "静音楼层 · 投屏电视", 55, 25)
        )),
        Brand("格林豪泰酒店", 208, listOf(
            room("大床房", "1张1.8m大床", "22㎡", "独立卫浴 · 高速Wi‑Fi", 0, 25),
            room("高级大床房", "1张2.0m大床", "26㎡", "商务书桌 · 投屏电视", 45, 25),
            room("标准房", "2张1.2m单人床", "24㎡", "独立卫浴 · 办公桌", 35, 25)
        ))
    )

    fun around(station: String): List<NearbyHotel> {
        val seed = station.hashCode().absoluteValue
        return brands.indices
            .sortedBy { (seed + it * 37) % brands.size }
            .take(7)
            .mapIndexed { index, sourceIndex ->
                val brand = brands[sourceIndex]
                val locationAdjustment = ((seed / (index + 1) + sourceIndex * 19) % 8) * 10
                val distance = 420 + ((seed + index * 683) % 4_380)
                NearbyHotel(
                    brand = brand.name,
                    name = "${brand.name}（${station}站店）",
                    distanceMeters = distance,
                    rating = 4.5 + ((seed + index * 3) % 5) / 10.0,
                    addressHint = "${station}站 ${if (index % 2 == 0) "东" else "西"}侧约${distance}米",
                    baseNightlyPrice = brand.basePrice + locationAdjustment,
                    rooms = brand.rooms
                )
            }
    }

    private fun room(
        name: String,
        bed: String,
        area: String,
        amenities: String,
        priceOffset: Int,
        breakfastPrice: Int
    ) = HotelRoom(
        name = name,
        bed = bed,
        area = area,
        amenities = amenities,
        priceOffset = priceOffset,
        breakfastPrice = breakfastPrice,
        cancellation = "入住日 18:00 前可免费取消"
    )
}
