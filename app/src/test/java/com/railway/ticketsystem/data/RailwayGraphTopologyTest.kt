package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.RouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RailwayGraphTopologyTest {
    @Test
    fun keepsPhysicalEdgesWhenStationCatalogIsEnriched() {
        val graph = RailwayGraph()
        graph.addStation(Station("甲", "A"))
        graph.addStation(Station("乙", "B"))
        graph.addStation(Station("丙", "C"))
        graph.addRoute("甲", "乙", 40, 12.0, 10)

        // RealRailwayRoutes imports stations a second time after its graph is ready.
        graph.addStation(Station("甲", "A", "测试市"))

        assertEquals(listOf("乙"), graph.getDirectNeighbours("甲").map { it.name })
        assertEquals(listOf("甲", "乙"), graph.findShortestPath("甲", "乙")!!.path)
        assertNull(graph.findShortestPath("甲", "丙"))
    }

    @Test
    fun neverMixesConventionalAndHighSpeedPhysicalNetworks() {
        val graph = RailwayGraph()
        graph.addStation(Station("高速起点", "A"))
        graph.addStation(Station("联络站", "B"))
        graph.addStation(Station("普速终点", "C"))
        graph.addRoute("高速起点", "联络站", 40, 12.0, 10, RouteType.HIGH_SPEED)
        graph.addRoute("联络站", "普速终点", 40, 12.0, 10, RouteType.CONVENTIONAL)

        assertEquals(listOf("联络站"), graph.getDirectNeighbours("高速起点", RouteType.HIGH_SPEED).map { it.name })
        assertEquals(listOf("普速终点"), graph.getDirectNeighbours("联络站", RouteType.CONVENTIONAL).map { it.name })
        assertNull(graph.findShortestPath("高速起点", "普速终点", RouteType.HIGH_SPEED))
    }

    @Test
    fun doesNotDuplicateAnAlreadyRegisteredPhysicalSection() {
        val graph = RailwayGraph()
        graph.addStation(Station("甲", "A"))
        graph.addStation(Station("乙", "B"))
        graph.addRoute("甲", "乙", 40, 12.0, 10)
        graph.addRoute("甲", "乙", 40, 12.0, 10)

        assertTrue(graph.getDirectNeighbours("甲").single().name == "乙")
    }
}
