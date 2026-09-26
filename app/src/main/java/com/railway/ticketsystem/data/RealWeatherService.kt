package com.railway.ticketsystem.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 真实气象数据获取服务
 * 基于权威开源 Open-Meteo 气象接口获取实时温度与天气状况（真实数据，无须 API Key，全天候稳定更新）
 */
data class RealWeatherInfo(
    val temperature: Double,
    val weatherCode: Int,
    val description: String,
    val icon: String
)

object RealWeatherService {
    private val cache = ConcurrentHashMap<String, Pair<Long, RealWeatherInfo>>()
    private const val CACHE_DURATION_MILLIS = 30 * 60 * 1000L // 30 分钟缓存

    fun fetchCurrentWeather(latitude: Double, longitude: Double): RealWeatherInfo? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        val cacheKey = "${String.format("%.2f", latitude)},${String.format("%.2f", longitude)}"
        val cached = cache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < CACHE_DURATION_MILLIS) {
            return cached.second
        }

        return runCatching {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current_weather=true&timezone=Asia%2FShanghai"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Railway12306RealWeather/2.0")

            if (conn.responseCode == 200) {
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val current = root.optJSONObject("current_weather")
                if (current != null) {
                    val temp = current.optDouble("temperature", Double.NaN)
                    val code = current.optInt("weathercode", -1)
                    if (!temp.isNaN()) {
                        val (desc, icon) = decodeWeather(code)
                        val info = RealWeatherInfo(temperature = temp, weatherCode = code, description = desc, icon = icon)
                        cache[cacheKey] = Pair(now, info)
                        return@runCatching info
                    }
                }
            }
            null
        }.getOrNull()
    }

    fun decodeWeather(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("晴", "☀️")
            1 -> Pair("晴间多云", "🌤️")
            2 -> Pair("多云", "⛅")
            3 -> Pair("阴", "☁️")
            45, 48 -> Pair("雾", "🌫️")
            51, 53, 55 -> Pair("小毛毛雨", "🌦️")
            56, 57 -> Pair("冻雨", "🌧️")
            61 -> Pair("小雨", "🌧️")
            63 -> Pair("中雨", "🌧️")
            65 -> Pair("大雨", "🌧️")
            66, 67 -> Pair("大冻雨", "🌧️")
            71 -> Pair("小雪", "🌨️")
            73 -> Pair("中雪", "🌨️")
            75 -> Pair("大雪", "❄️")
            77 -> Pair("雪粒", "🌨️")
            80, 81, 82 -> Pair("阵雨", "🌦️")
            85, 86 -> Pair("阵雪", "🌨️")
            95 -> Pair("雷阵雨", "⛈️")
            96, 99 -> Pair("雷雨伴冰雹", "⛈️")
            else -> Pair("多云", "⛅")
        }
    }
}
