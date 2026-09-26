package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class RouteMapSnappingTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RailwayGraphManager.initializeRealRoutes()
        }
    }

    @Test
    fun testFuzhouSouthToNanchangWestRoute() {
        val minStops = RailwayRouteManager.getRouteStationsMinStops("福州南", "南昌西", RouteType.HIGH_SPEED)
        assertTrue(minStops.contains("福州") && minStops.contains("抚州"))
        assertFalse("不应通过普速衢宁铁路绕远", minStops.contains("庆元"))

        val resolvedDirect = PhysicalRailCorridorResolver.resolveForMap(listOf("福州南", "南昌西"), MapRailNetwork.HIGH_SPEED)
        assertEquals("末尾站点不应出现重复", "南昌西", resolvedDirect.stationNames.last())
        assertNotEquals(resolvedDirect.stationNames[resolvedDirect.stationNames.size - 2], "南昌西")

        val resolvedViaShangrao = PhysicalRailCorridorResolver.resolveForMap(listOf("福州南", "上饶", "南昌西"), MapRailNetwork.HIGH_SPEED)
        assertTrue(resolvedViaShangrao.stationNames.contains("上饶"))
        assertTrue(resolvedViaShangrao.stationNames.contains("五府山"))
        assertTrue(resolvedViaShangrao.stationNames.contains("武夷山北"))
        assertTrue(resolvedViaShangrao.stationNames.contains("鹰潭北"))
        assertTrue("合福段应提示合福高铁", resolvedViaShangrao.legCorridorHints.contains("合福高铁"))
        assertTrue("沪昆段应提示沪昆高铁", resolvedViaShangrao.legCorridorHints.contains("沪昆高铁"))

        val wuyiStations = listOf("汉口", "汉川北", "天门", "京山南", "钟祥南", "荆门西", "当阳西", "宜昌北", "宜昌东")
        val resolvedWuyi = PhysicalRailCorridorResolver.resolveForMap(wuyiStations, MapRailNetwork.HIGH_SPEED)
        assertTrue("武宜高铁应独立提示武宜高铁，不走宁蓉", resolvedWuyi.legCorridorHints.all { it == "武宜高铁" })

        val ningrongHanyi = listOf("汉口", "汉川", "天门南", "仙桃西", "潜江", "荆州", "枝江北", "宜昌东")
        val resolvedNingrong = PhysicalRailCorridorResolver.resolveForMap(ningrongHanyi, MapRailNetwork.HIGH_SPEED)
        assertTrue("宁蓉汉宜段应提示宁蓉铁路", resolvedNingrong.legCorridorHints.all { it == "宁蓉铁路" })

        val hefuFull = listOf("福州南", "合肥南")
        val resolvedHefu = PhysicalRailCorridorResolver.resolveForMap(hefuFull, MapRailNetwork.HIGH_SPEED)
        assertTrue(resolvedHefu.stationNames.contains("黄山北"))
        assertTrue(resolvedHefu.stationNames.contains("婺源"))
        assertTrue(resolvedHefu.stationNames.contains("上饶"))
        assertTrue(resolvedHefu.legCorridorHints.all { it == "合福高铁" })

        // Connector corridors
        val hangwen = listOf("桐庐东", "浦江", "义乌", "横店", "磐安", "仙居", "楠溪江", "温州北", "温州南")
        val resolvedHangwen = PhysicalRailCorridorResolver.resolveForMap(hangwen, MapRailNetwork.HIGH_SPEED)
        assertTrue("杭温高铁应提示杭温高铁", resolvedHangwen.legCorridorHints.all { it == "杭温高铁" })

        val meilong = listOf("梅州西", "兴宁南", "五华", "龙川西")
        val resolvedMeilong = PhysicalRailCorridorResolver.resolveForMap(meilong, MapRailNetwork.HIGH_SPEED)
        assertTrue("梅龙高铁应提示梅龙高铁", resolvedMeilong.legCorridorHints.all { it == "梅龙高铁" })

        val chihuang = listOf("池州", "九华山", "黄山西", "黄山北")
        val resolvedChihuang = PhysicalRailCorridorResolver.resolveForMap(chihuang, MapRailNetwork.HIGH_SPEED)
        assertTrue("池黄高铁应提示池黄高铁", resolvedChihuang.legCorridorHints.all { it == "池黄高铁" })

        val guangzhan = listOf("广州白云", "佛山", "新兴南", "阳春东", "阳江北", "马踏", "茂名南", "吴川", "湛江北")
        val resolvedGuangzhan = PhysicalRailCorridorResolver.resolveForMap(guangzhan, MapRailNetwork.HIGH_SPEED)
        assertTrue("广湛高铁应提示广湛高铁", resolvedGuangzhan.legCorridorHints.all { it == "广湛高铁" })

        val wuhanHub = listOf("武汉", "汉口", "武昌")
        val resolvedWuhanHub = PhysicalRailCorridorResolver.resolveForMap(wuhanHub, MapRailNetwork.HIGH_SPEED)
        assertTrue("武汉枢纽应提示武汉枢纽连接线", resolvedWuhanHub.legCorridorHints.all { it == "武汉枢纽连接线" })

        val nanchangHub = listOf("南昌西", "南昌")
        val resolvedNanchangHub = PhysicalRailCorridorResolver.resolveForMap(nanchangHub, MapRailNetwork.HIGH_SPEED)
        assertTrue("南昌枢纽应提示南昌枢纽联络线", resolvedNanchangHub.legCorridorHints.all { it == "南昌枢纽联络线" })

        val xianHub = listOf("西安北", "西安东")
        val resolvedXianHub = PhysicalRailCorridorResolver.resolveForMap(xianHub, MapRailNetwork.HIGH_SPEED)
        assertTrue("西安枢纽应提示西安枢纽高速联络线", resolvedXianHub.legCorridorHints.all { it == "西安枢纽高速联络线" })
    }
}
