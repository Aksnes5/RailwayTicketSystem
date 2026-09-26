package com.railway.ticketsystem.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 行程行李备忘与防遗忘清单持久化仓储
 */
class TripChecklistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("trip_checklists", Context.MODE_PRIVATE)

    data class ChecklistItem(
        val id: String,
        val text: String,
        val category: String,
        var isChecked: Boolean
    )

    private val defaultItems = listOf(
        ChecklistItem("1", "居民身份证 / 有效身份证件", "证件必备", true),
        ChecklistItem("2", "手机与移动电源 (额定能量≤100Wh)", "电子设备", true),
        ChecklistItem("3", "降噪耳机 / 乘车隔音耳塞", "旅途舒适", false),
        ChecklistItem("4", "慢病常备用药 / 应急晕车贴", "健康防护", false),
        ChecklistItem("5", "便携保温水杯 (车站提供开水)", "日常随身", false),
        ChecklistItem("6", "随身保暖外套 (车厢冷气调节)", "穿搭准备", false)
    )

    fun getItems(tripKey: String): List<ChecklistItem> {
        val jsonStr = prefs.getString("checklist_$tripKey", null) ?: return defaultItems
        return runCatching {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ChecklistItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChecklistItem(
                        id = obj.getString("id"),
                        text = obj.getString("text"),
                        category = obj.optString("category", "自定义"),
                        isChecked = obj.getBoolean("isChecked")
                    )
                )
            }
            list
        }.getOrDefault(defaultItems)
    }

    fun saveItems(tripKey: String, items: List<ChecklistItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("category", item.category)
                put("isChecked", item.isChecked)
            }
            arr.put(obj)
        }
        prefs.edit().putString("checklist_$tripKey", arr.toString()).apply()
    }

    fun toggleItem(tripKey: String, itemId: String): List<ChecklistItem> {
        val list = getItems(tripKey)
        list.firstOrNull { it.id == itemId }?.let {
            it.isChecked = !it.isChecked
        }
        saveItems(tripKey, list)
        return list
    }

    fun addItem(tripKey: String, text: String): List<ChecklistItem> {
        val list = getItems(tripKey).toMutableList()
        val newItem = ChecklistItem(
            id = System.currentTimeMillis().toString(),
            text = text,
            category = "个性添加",
            isChecked = false
        )
        list.add(newItem)
        saveItems(tripKey, list)
        return list
    }
}
