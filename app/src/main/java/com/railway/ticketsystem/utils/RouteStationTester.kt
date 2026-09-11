package com.railway.ticketsystem.utils

import android.util.Log
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RealRailwayRoutes

/**
 * 途径车站功能测试工具
 */
object RouteStationTester {
    
    private const val TAG = "RouteStationTester"
    
    /**
     * 测试途径车站功能
     */
    fun testRouteStations() {
        Log.d(TAG, "开始测试途径车站功能")
        
        try {
            // 测试宁蓉铁路的途径车站
            testNingrongRoute()
            
            // 测试京港高铁的途径车站
            testJinggangRoute()
            
            // 测试其他线路
            testOtherRoutes()
            
        } catch (e: Exception) {
            Log.e(TAG, "测试途径车站功能时出错: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun testNingrongRoute() {
        Log.d(TAG, "测试宁蓉铁路途径车站")
        
        // 测试南京南到成都东
        val stations1 = RailwayData.getRouteStations("南京南", "成都东")
        Log.d(TAG, "南京南到成都东途径车站数量: ${stations1.size}")
        Log.d(TAG, "途径车站: ${stations1.joinToString(" -> ")}")
        
        // 测试合肥南到重庆北
        val stations2 = RailwayData.getRouteStations("合肥南", "重庆北")
        Log.d(TAG, "合肥南到重庆北途径车站数量: ${stations2.size}")
        Log.d(TAG, "途径车站: ${stations2.joinToString(" -> ")}")
        
        // 测试反向
        val stations3 = RailwayData.getRouteStations("成都东", "南京南")
        Log.d(TAG, "成都东到南京南途径车站数量: ${stations3.size}")
        Log.d(TAG, "途径车站: ${stations3.joinToString(" -> ")}")
    }
    
    private fun testJinggangRoute() {
        Log.d(TAG, "测试京港高铁途径车站")
        
        // 测试北京丰台到香港西九龙
        val stations1 = RailwayData.getRouteStations("北京丰台", "香港西九龙")
        Log.d(TAG, "北京丰台到香港西九龙途径车站数量: ${stations1.size}")
        Log.d(TAG, "途径车站: ${stations1.joinToString(" -> ")}")
        
        // 测试南昌东到深圳北
        val stations2 = RailwayData.getRouteStations("南昌东", "深圳北")
        Log.d(TAG, "南昌东到深圳北途径车站数量: ${stations2.size}")
        Log.d(TAG, "途径车站: ${stations2.joinToString(" -> ")}")
    }
    
    private fun testOtherRoutes() {
        Log.d(TAG, "测试其他线路途径车站")
        
        // 测试昌福铁路
        val stations1 = RailwayData.getRouteStations("南昌西", "福州")
        Log.d(TAG, "南昌西到福州途径车站数量: ${stations1.size}")
        Log.d(TAG, "途径车站: ${stations1.joinToString(" -> ")}")
        
        // 测试厦深铁路
        val stations2 = RailwayData.getRouteStations("厦门北", "深圳北")
        Log.d(TAG, "厦门北到深圳北途径车站数量: ${stations2.size}")
        Log.d(TAG, "途径车站: ${stations2.joinToString(" -> ")}")
        
        // 测试沪宁城际
        val stations3 = RailwayData.getRouteStations("南京", "上海")
        Log.d(TAG, "南京到上海途径车站数量: ${stations3.size}")
        Log.d(TAG, "途径车站: ${stations3.joinToString(" -> ")}")
    }
    
    /**
     * 测试特定线路的途径车站
     */
    fun testSpecificRoute(fromStation: String, toStation: String) {
        Log.d(TAG, "测试特定线路: $fromStation -> $toStation")
        
        try {
            val stations = RailwayData.getRouteStations(fromStation, toStation)
            Log.d(TAG, "途径车站数量: ${stations.size}")
            Log.d(TAG, "途径车站: ${stations.joinToString(" -> ")}")
            
            if (stations.isEmpty()) {
                Log.w(TAG, "警告: 没有找到途径车站，可能是直达车次或线路不存在")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "测试特定线路时出错: ${e.message}")
            e.printStackTrace()
        }
    }
}


