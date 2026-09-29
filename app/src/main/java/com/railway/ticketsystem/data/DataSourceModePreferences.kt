package com.railway.ticketsystem.data

import android.content.Context

/**
 * 车次数据源模式
 */
enum class TrainDataSourceMode(
    val title: String,
    val shortBadge: String,
    val description: String
) {
    REAL(
        "12306 时刻库",
        "时刻库",
        "采用内置经核验的车次、经停与票价快照，并随应用数据版本更新"
    ),
    SIMULATED(
        "全国路网方案",
        "全网路网",
        "依据全国铁路网拓扑与运行规则提供覆盖性查询方案"
    )
}

/**
 * 数据源模式持久化首选项
 */
object DataSourceModePreferences {
    private const val PREFS_NAME = "train_data_source_mode_prefs"
    private const val KEY_MODE = "data_source_mode"

    @Volatile
    private var currentMode: TrainDataSourceMode = TrainDataSourceMode.REAL

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = sp.getString(KEY_MODE, TrainDataSourceMode.REAL.name)
        currentMode = runCatching { TrainDataSourceMode.valueOf(modeStr ?: "") }.getOrDefault(TrainDataSourceMode.REAL)
    }

    fun getMode(context: Context? = null): TrainDataSourceMode {
        if (context != null) {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeStr = sp.getString(KEY_MODE, currentMode.name)
            currentMode = runCatching { TrainDataSourceMode.valueOf(modeStr ?: "") }.getOrDefault(currentMode)
        }
        return currentMode
    }

    fun setMode(context: Context, mode: TrainDataSourceMode) {
        currentMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
        // 清理车次缓存，确保下一次查询立即使用全新数据源
        ServiceSeparatedTrainCatalog.clearCache()
    }

    fun toggleMode(context: Context): TrainDataSourceMode {
        val next = if (getMode(context) == TrainDataSourceMode.REAL) {
            TrainDataSourceMode.SIMULATED
        } else {
            TrainDataSourceMode.REAL
        }
        setMode(context, next)
        return next
    }

    fun isRealMode(context: Context? = null): Boolean {
        return getMode(context) == TrainDataSourceMode.REAL
    }
}
