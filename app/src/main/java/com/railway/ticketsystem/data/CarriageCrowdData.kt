package com.railway.ticketsystem.data

/**
 * 列车车厢客座热力与大件行李架透视数据
 */
object CarriageCrowdData {

    enum class CrowdLevel(val label: String, val badgeColor: String, val textColor: String) {
        SPACIOUS("宽松", "#E8F8F0", "#1D8348"),
        MODERATE("适中", "#FFF9E6", "#B7791F"),
        BUSY("较满", "#FEECEC", "#C53030")
    }

    data class CarriageInfo(
        val carNumber: Int,
        val carType: String,
        val crowd: CrowdLevel,
        val hasLuggageRack: Boolean,
        val isQuietCar: Boolean,
        val isDiningCar: Boolean,
        val hasAccessibleToilet: Boolean,
        val availableCount: Int
    )

    /**
     * 生成标准 8 节或 16 节动车组车厢透视数据
     */
    fun getCarriages(trainNumber: String, isHighSpeed: Boolean = true): List<CarriageInfo> {
        val seed = trainNumber.hashCode()
        return listOf(
            CarriageInfo(1, "一等座", CrowdLevel.SPACIOUS, true, false, false, false, 18),
            CarriageInfo(2, "二等座", CrowdLevel.MODERATE, true, false, false, false, 24),
            CarriageInfo(3, "二等座", CrowdLevel.SPACIOUS, false, true, false, false, 31),
            CarriageInfo(4, "二等座", CrowdLevel.BUSY, true, false, false, false, 8),
            CarriageInfo(5, "餐车/二等", CrowdLevel.MODERATE, false, false, true, true, 12),
            CarriageInfo(6, "二等座", CrowdLevel.SPACIOUS, true, false, false, false, 26),
            CarriageInfo(7, "二等座", CrowdLevel.MODERATE, true, false, false, false, 19),
            CarriageInfo(8, "二等/商务", CrowdLevel.SPACIOUS, true, false, false, false, 15)
        )
    }
}
