package com.railway.ticketsystem.activity

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.RailwayData

class TestRoutesActivity : ImmersiveActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_routes)
        
        val tvTestInfo = findViewById<TextView>(R.id.tvTestInfo)
        
        // 测试线路数据
        val testInfo = StringBuilder()
        
        // 测试汉口-天门南线路
        testInfo.append("=== 汉口-天门南线路测试 ===\n")
        val hankouTianmenTrains = RailwayData.getTrainsSortedByTime("汉口", "天门南")
        testInfo.append("找到车次数量: ${hankouTianmenTrains.size}\n")
        
        if (hankouTianmenTrains.isNotEmpty()) {
            val firstTrain = hankouTianmenTrains.first()
            testInfo.append("第一班车: ${firstTrain.number}\n")
            testInfo.append("出发时间: ${firstTrain.departureTime}\n")
            testInfo.append("到达时间: ${firstTrain.arrivalTime}\n")
            testInfo.append("价格: ¥${firstTrain.price}\n")
        }
        
        // 测试途径车站
        val routeStations = RailwayData.getRouteStations("汉口", "天门南")
        testInfo.append("途径车站: ${routeStations.joinToString(" → ")}\n")
        
        // 测试价格计算
        val price = RailwayData.getPriceBetweenStations("汉口", "天门南")
        testInfo.append("计算价格: ¥$price\n")
        
        // 测试武汉-广州南线路
        testInfo.append("\n=== 武汉-广州南线路测试 ===\n")
        val wuhanGuangzhouTrains = RailwayData.getTrainsSortedByTime("武汉", "广州南")
        testInfo.append("找到车次数量: ${wuhanGuangzhouTrains.size}\n")
        
        if (wuhanGuangzhouTrains.isNotEmpty()) {
            val firstTrain = wuhanGuangzhouTrains.first()
            testInfo.append("第一班车: ${firstTrain.number}\n")
            testInfo.append("出发时间: ${firstTrain.departureTime}\n")
            testInfo.append("到达时间: ${firstTrain.arrivalTime}\n")
            testInfo.append("价格: ¥${firstTrain.price}\n")
        }
        
        // 测试途径车站
        val wuhanGuangzhouStations = RailwayData.getRouteStations("武汉", "广州南")
        testInfo.append("途径车站: ${wuhanGuangzhouStations.joinToString(" → ")}\n")
        
        // 测试价格计算
        val wuhanGuangzhouPrice = RailwayData.getPriceBetweenStations("武汉", "广州南")
        testInfo.append("计算价格: ¥$wuhanGuangzhouPrice\n")
        
        // 显示所有线路
        testInfo.append("\n=== 所有线路 ===\n")
        val allRoutes = RailwayData.getAllRoutes()
        allRoutes.forEach { route ->
            testInfo.append("${route.routeName}: ${route.stations.first().name} → ${route.stations.last().name}\n")
        }
        
        tvTestInfo.text = testInfo.toString()
    }
}
