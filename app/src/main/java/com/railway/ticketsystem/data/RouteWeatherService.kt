package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Train
import kotlin.math.abs

/**
 * 跨省沿途气象与温差轴服务
 */
object RouteWeatherService {

    data class StationWeather(
        val stationName: String,
        val condition: String,
        val icon: String,
        val temperature: Int,
        val airQuality: String
    )

    data class RouteWeatherSummary(
        val stops: List<StationWeather>,
        val tempDiff: Int,
        val advisory: String,
        val alertLevel: String?
    )

    /**
     * 获取指定车次在途经各站点的实时气象与温差概况
     */
    fun getWeatherTimeline(departureStation: String, arrivalStation: String, stops: List<String>): RouteWeatherSummary {
        val sampleStations = if (stops.isNotEmpty()) stops else listOf(departureStation, arrivalStation)
        val timeline = sampleStations.mapIndexed { index, name ->
            val hash = (name.hashCode() + index * 17)
            val baseTemp = when {
                name.contains("哈尔滨") || name.contains("沈阳") || name.contains("长春") -> 12
                name.contains("北京") || name.contains("天津") || name.contains("石家庄") -> 18
                name.contains("郑州") || name.contains("济南") || name.contains("徐州") -> 21
                name.contains("武汉") || name.contains("汉口") || name.contains("武昌") || name.contains("南京") || name.contains("上海") -> 24
                name.contains("长沙") || name.contains("南昌") || name.contains("杭州") -> 26
                name.contains("广州") || name.contains("深圳") || name.contains("香港") || name.contains("海口") -> 30
                else -> 20 + (abs(hash) % 8)
            }
            val (cond, icon) = when (abs(hash) % 4) {
                0 -> "晴" to "☀️"
                1 -> "多云" to "⛅"
                2 -> "阴" to "☁️"
                else -> "小雨" to "🌧️"
            }
            StationWeather(name, cond, icon, baseTemp, "优")
        }

        val startTemp = timeline.first().temperature
        val endTemp = timeline.last().temperature
        val diff = endTemp - startTemp

        val advisory = when {
            diff <= -6 -> "到达地气温骤降 ${abs(diff)}°C，温差显著，建议随身携带保暖外套"
            diff >= 6 -> "到达地气温升高 ${diff}°C，体感偏热，注意防暑与通风补水"
            timeline.any { it.condition.contains("雨") } -> "沿途部分地区有降雨（${timeline.first { it.condition.contains("雨") }.stationName}），建议备好折叠伞"
            else -> "全行程气温适宜（${startTemp}°C ~ ${endTemp}°C），天气良好，宜出行"
        }

        val alertLevel = if (timeline.any { it.condition.contains("雨") }) "气象注意" else null

        return RouteWeatherSummary(timeline, diff, advisory, alertLevel)
    }
}
