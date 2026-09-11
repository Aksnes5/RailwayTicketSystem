package com.railway.ticketsystem

import com.railway.ticketsystem.data.RailwayGraphManager
import com.railway.ticketsystem.debug.TransferWorkload
import org.junit.Test

/**
 * 中转搜索的耗时基准，跑在 JVM 上。与真机探针共用 TransferWorkload，两边数字可直接对照。
 *
 * 做这个的理由：这一段改过四轮，每轮都是读代码猜瓶颈，每轮都猜错（重复监听器、异步化、
 * 砍候选站、记忆化）。先把"时间花在哪一段"量出来，再决定改哪里。
 */
class TransferSearchPerfTest {

    @Test
    fun measureTransferSearchWorkload() {
        RailwayGraphManager.initializeRealRoutes()
        println("[perf] $DEPARTURE→$ARRIVAL ${TransferWorkload.run(DEPARTURE, ARRIVAL)}")
    }

    /**
     * 真机上卡住的那一对（汉口→庐山）。
     *
     * 顺带守住 initializeRoutes() 的幂等性：上面 measureTransferSearchWorkload 已经装过一次，
     * 这里再装一次；如果保护失效，图上的边数会翻倍，下面打印的图规模会立刻看出来。
     */
    @Test
    fun probeHankouToLushan() {
        RailwayGraphManager.initializeRealRoutes()
        println("[perf] $LUSHAN_PAIR ${TransferWorkload.run("汉口", "庐山")}")
    }

    private companion object {
        const val DEPARTURE = "北京西"
        const val ARRIVAL = "上海虹桥"
        const val LUSHAN_PAIR = "汉口→庐山"
    }
}
