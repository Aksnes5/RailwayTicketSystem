package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
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
    fun doesNotDuplicateAnAlreadyRegisteredPhysicalSection() {
        val graph = RailwayGraph()
        graph.addStation(Station("甲", "A"))
        graph.addStation(Station("乙", "B"))
        graph.addRoute("甲", "乙", 40, 12.0, 10)
        graph.addRoute("甲", "乙", 40, 12.0, 10)

        assertTrue(graph.getDirectNeighbours("甲").single().name == "乙")
    }
}
