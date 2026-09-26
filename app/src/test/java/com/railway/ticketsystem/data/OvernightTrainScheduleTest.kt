package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.isDongwo
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class OvernightTrainScheduleTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RailwayGraphManager.initializeRealRoutes()
        }
    }

    private fun parseToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun isOvernight(time: String): Boolean {
        val minutes = parseToMinutes(time)
        return minutes >= 22 * 60 || minutes < 6 * 60
    }

    @Test
    fun conventionalRoutesHaveLargeNumberOfOvernightTrains() {
        val conventionalTrains = TrainGenerator.generateTrainsForPair(
            from = "北京",
            to = "广州",
            minCount = 40,
            maxCount = 60,
            routeType = RouteType.CONVENTIONAL
        )
        assertFalse("普速列车列表不应为空", conventionalTrains.isEmpty())

        // 统计 22:00 - 06:00 时段车次
        val overnightTrains = conventionalTrains.filter { isOvernight(it.departureTime) }
        val eveningTrains = conventionalTrains.filter {
            val m = parseToMinutes(it.departureTime)
            m in (22 * 60 until 24 * 60)
        }
        val earlyMorningTrains = conventionalTrains.filter {
            val m = parseToMinutes(it.departureTime)
            m in (0 until 6 * 60)
        }

        println("【普速列车测试】总车次: ${conventionalTrains.size}, 凌晨/夜间(22:00-06:00)车次: ${overnightTrains.size}")
        println("  其中 22:00-23:59 发车: ${eveningTrains.size}, 00:00-06:00 发车: ${earlyMorningTrains.size}")

        // 验证安排了大量普速车（至少占总车次的 40% 以上）
        assertTrue(
            "凌晨/夜间普速车应大量存在 (实际 ${overnightTrains.size} / ${conventionalTrains.size})",
            overnightTrains.size >= conventionalTrains.size * 0.40
        )
        assertTrue("22:00-23:59 期间应有普速车", eveningTrains.isNotEmpty())
        assertTrue("00:00-06:00 期间应有大量普速车", earlyMorningTrains.size >= 10)

        // 验证所有普速车次前缀均为 K、T、Z
        assertTrue("所有普速车次应为 K/T/Z 字头", conventionalTrains.all { train ->
            train.number.startsWith("K") || train.number.startsWith("T") || train.number.startsWith("Z")
        })
    }

    @Test
    fun highSpeedRoutesInOvernightWindowOnlyContainDongwoD1ToD300() {
        val highSpeedTrains = TrainGenerator.generateTrainsForPair(
            from = "北京西",
            to = "广州南",
            minCount = 40,
            maxCount = 60,
            routeType = RouteType.HIGH_SPEED
        )
        assertFalse("高铁车次列表不应为空", highSpeedTrains.isEmpty())

        val overnightTrains = highSpeedTrains.filter { isOvernight(it.departureTime) }
        val daytimeTrains = highSpeedTrains.filter { !isOvernight(it.departureTime) }

        println("【高铁车次测试】总车次: ${highSpeedTrains.size}, 凌晨/夜间(22:00-06:00)车次: ${overnightTrains.size}, 日间车次: ${daytimeTrains.size}")
        overnightTrains.forEach {
            println("  夜间动车: ${it.number} 发车时间: ${it.departureTime}")
        }

        // 高铁动车在夜间“不应该没有”
        assertTrue("高铁动车线网在 22:00-06:00 应有车次存在", overnightTrains.isNotEmpty())

        // 除了普速，只应该出现动卧（D1-D300）
        for (train in overnightTrains) {
            assertTrue("凌晨/夜间除了普速只应出现动卧（D1-D300），实际车次为: ${train.number}", isDongwo(train.number))
            val numberInt = train.number.substring(1).toInt()
            assertTrue("动卧车号应在 1..300 范围内: ${train.number}", numberInt in 1..300)
            assertFalse("凌晨/夜间绝不应出现 G 字头高铁", train.number.startsWith("G"))
            assertFalse("凌晨/夜间绝不应出现 C 字头城际", train.number.startsWith("C"))
        }

        // 日间车次仍正常运行
        assertTrue("日间车次应占主要比例", daytimeTrains.size > overnightTrains.size)
    }

    @Test
    fun isDongwoHelperCorrectlyIdentifiesNumbers() {
        assertTrue(isDongwo("D1"))
        assertTrue(isDongwo("D2"))
        assertTrue(isDongwo("D28"))
        assertTrue(isDongwo("D100"))
        assertTrue(isDongwo("D300"))
        assertTrue(isDongwo("d150"))

        assertFalse(isDongwo("D301"))
        assertFalse(isDongwo("D2001"))
        assertFalse(isDongwo("G1"))
        assertFalse(isDongwo("C2001"))
        assertFalse(isDongwo("K123"))
        assertFalse(isDongwo("T45"))
        assertFalse(isDongwo("Z99"))
        assertFalse(isDongwo(""))
    }

    @Test
    fun conventionalTrainsNumberingRulesAreRespected() {
        val trains = TrainGenerator.generateTrainsForPair(
            from = "北京",
            to = "广州",
            minCount = 50,
            maxCount = 80,
            routeType = RouteType.CONVENTIONAL
        )
        assertFalse(trains.isEmpty())

        val tTrains = trains.filter { it.number.startsWith("T") }
        val zTrains = trains.filter { it.number.startsWith("Z") }
        val kTrains = trains.filter { it.number.startsWith("K") }

        println("【车次编号规则测试】T字头: ${tTrains.size}, Z字头: ${zTrains.size}, K字头: ${kTrains.size}")

        // T字头严格 1-3 位数 (1-998)，绝无四位数
        for (train in tTrains) {
            val num = train.number.substring(1).toInt()
            assertTrue("T字头必须为1-3位数 (1-998): ${train.number}", num in 1..998)
            assertTrue("T字头长度应<=4 (T+3位): ${train.number}", train.number.length <= 4)
        }

        // Z字头严格 1-3 位数 (1-998)，绝无四位数
        for (train in zTrains) {
            val num = train.number.substring(1).toInt()
            assertTrue("Z字头必须为1-3位数 (1-998): ${train.number}", num in 1..998)
            assertTrue("Z字头长度应<=4 (Z+3位): ${train.number}", train.number.length <= 4)
        }

        // K字头有四位数
        val fourDigitK = kTrains.filter { it.number.substring(1).toInt() >= 1000 }
        assertTrue("K字头应当包含四位数车次，实际找到: ${fourDigitK.size}", fourDigitK.isNotEmpty())
    }

    @Test
    fun changshaToWuchangRunningMinutesIsRealistic() {
        val runningMinutes = TrainStopSchedulePlanner.segmentRunningMinutes("长沙", "武昌", "T101")
        println("【长沙-武昌耗时测试】实际计算耗时: ${runningMinutes}分钟 (${runningMinutes / 60}小时${runningMinutes % 60}分)")

        // 长沙到武昌约 360 公里，普速列车耗时通常在 2 小时到 4 小时之间，绝不能是 19 分钟
        assertTrue("长沙到武昌耗时应大于 100 分钟，绝不能是十多分钟，实际: $runningMinutes", runningMinutes >= 100)
        assertTrue("长沙到武昌耗时应小于 300 分钟，实际: $runningMinutes", runningMinutes <= 300)

        // 测试 createAnchored 还原武昌-北京时刻表时，起点长沙的时刻正常
        val anchored = TrainStopSchedulePlanner.createAnchored(
            stations = listOf("长沙", "武昌", "信阳", "郑州", "石家庄", "北京"),
            trainNumber = "T101",
            queryDepartureStation = "武昌",
            queryArrivalStation = "北京",
            queryDepartureTime = "01:55",
            queryDuration = "11小时53分钟"
        )
        val changsha = anchored.stops.first { it.stationName == "长沙" }
        val wuchang = anchored.stops.first { it.stationName == "武昌" }
        println("  长沙发车: ${changsha.departureTime}, 武昌到点: ${wuchang.arrivalTime}")

        // 验证武昌到点与长沙发车间隔合理 (至少 100 分钟)
        val wuchangArrivalMinutes = parseToMinutes(wuchang.arrivalTime)
        val changshaDepartureMinutes = parseToMinutes(changsha.departureTime)
        // 跨夜计算时间差
        val diff = ((wuchangArrivalMinutes - changshaDepartureMinutes) % 1440 + 1440) % 1440
        println("  长沙到武昌实际运行间隔: ${diff}分钟")
        assertTrue("长沙到武昌实际运行间隔应大于 100 分钟，实际: $diff", diff >= 100)
    }
}
