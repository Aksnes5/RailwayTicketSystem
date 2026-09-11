package com.railway.ticketsystem.debug

import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RealRailwayRoutes
import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain

/**
 * 中转搜索工作量的复刻：候选站怎么选、两段车次怎么生成、怎么两两组合、怎么排序截断，
 * 都照着 SearchResultsActivity.findTransferRoutes 抄一遍，只留下计数和分段耗时。
 *
 * 真机探针（TransferPerfProbe）和 JVM 基准（TransferSearchPerfTest）共用这一份，
 * 免得两边各测各的、数字对不上。
 *
 * 注意这是**复刻**不是调用：findTransferRoutes 是 Activity 的私有方法，外面调不到。
 * 那边改了这里要跟着改，否则测的就不是线上真正跑的东西。
 */
object TransferWorkload {

    data class Result(
        val initMs: Long,
        val candidateCount: Int,
        val pairsWithService: Int,
        val generationMs: Long,
        val combinationMs: Long,
        val sortMs: Long,
        val transferCount: Int,
        val shownCount: Int,
        val totalMs: Long,
        val probe: String
    ) {
        override fun toString(): String =
            "初始化=${initMs}ms 候选中转站=$candidateCount（两段都有车 $pairsWithService）" +
                " 生成=${generationMs}ms 组合=${combinationMs}ms（$transferCount 条）" +
                " 排序截断=${sortMs}ms（取 $shownCount 条） 合计=${totalMs}ms | $probe"
    }

    /**
     * @param onStation 每开始处理一个候选站回调一次。一旦搜索卡死，最后打印出来的就是卡住
     *                  的那个站——比事后读代码猜快得多。
     * @param availabilityPenalty 传入时按线上那条比较器排序（查两段二等座库存），
     *                  传 null 时退化成只按耗时/价格排。线上真正的开销就在这个查库存上，
     *                  复刻里省掉它就会严重低估排序耗时。
     */
    fun run(
        departure: String,
        arrival: String,
        onStation: ((String) -> Unit)? = null,
        availabilityPenalty: ((Train) -> Boolean)? = null
    ): Result {
        // 路网要在同一份数据上比：冷启动那次把线路装进图里也要计时，真机上它不一定是小头。
        val initStart = System.nanoTime()
        RailwayData.preloadData()
        val initMs = elapsedMs(initStart)

        val candidates = transferCandidates(departure, arrival)
        RailwayRouteManager.Probe.reset()
        val totalStart = System.nanoTime()

        val generationStart = System.nanoTime()
        val legs = ArrayList<Pair<String, Pair<List<Train>, List<Train>>>>(candidates.size)
        candidates.forEach { transfer ->
            onStation?.invoke(transfer)
            val first = RailwayData.getTrainsSortedByTime(departure, transfer)
            val second = RailwayData.getTrainsSortedByTime(transfer, arrival)
            if (first.isNotEmpty() && second.isNotEmpty()) legs.add(transfer to (first to second))
        }
        val generationMs = elapsedMs(generationStart)

        val combinationStart = System.nanoTime()
        val combinations = ArrayList<TransferTrain>()
        legs.forEach { (station, legPair) ->
            val (firstLeg, secondLeg) = legPair
            for (firstTrain in firstLeg) {
                for (secondTrain in secondLeg) {
                    val transferTime = transferTime(firstTrain, secondTrain)
                    if (transferTime in 20..360) {
                        combinations.add(
                            TransferTrain(
                                firstLeg = firstTrain,
                                secondLeg = secondTrain,
                                transferStation = station,
                                transferTime = transferTime,
                                totalPrice = firstTrain.price + secondTrain.price,
                                totalDuration = totalDuration(
                                    firstTrain.duration, secondTrain.duration, transferTime
                                )
                            )
                        )
                    }
                }
            }
        }
        val combinationMs = elapsedMs(combinationStart)

        val sortStart = System.nanoTime()
        val shown = (if (availabilityPenalty != null) {
            val penalty = availabilityPenalty
            combinations.sortedWith(
                compareBy<TransferTrain> { if (penalty(it.firstLeg) || penalty(it.secondLeg)) 1 else 0 }
                    .thenBy { riskRank(it) }
                    .thenBy { parseDurationToMinutes(it.totalDuration) }
                    .thenBy { it.totalPrice }
            )
        } else {
            combinations.sortedBy { parseDurationToMinutes(it.totalDuration) }
        })
            .take(20)
            .map { transferTrain ->
                Train(
                    number = transferTrain.displayNumber,
                    departureStation = transferTrain.departureStation,
                    arrivalStation = transferTrain.arrivalStation,
                    departureTime = transferTrain.departureTime,
                    arrivalTime = transferTrain.arrivalTime,
                    duration = transferTrain.totalDuration,
                    price = transferTrain.totalPrice,
                    availableSeats = transferTrain.availableSeats
                )
            }
        val sortMs = elapsedMs(sortStart)

        return Result(
            initMs = initMs,
            candidateCount = candidates.size,
            pairsWithService = legs.size,
            generationMs = generationMs,
            combinationMs = combinationMs,
            sortMs = sortMs,
            transferCount = combinations.size,
            shownCount = shown.size,
            totalMs = elapsedMs(totalStart),
            probe = RailwayRouteManager.Probe.summary()
        )
    }

    /** 与 SearchResultsActivity.findTransferRoutes 相同的候选站收集规则。 */
    private fun transferCandidates(departure: String, arrival: String): List<String> {
        val stations = LinkedHashSet<String>()
        RealRailwayRoutes.getAllRoutes().forEach { route ->
            val names = route.stations.map { it.name }
            val departureIndex = names.indexOf(departure)
            val arrivalIndex = names.indexOf(arrival)
            when {
                departureIndex >= 0 && arrivalIndex >= 0 -> {
                    val start = minOf(departureIndex, arrivalIndex)
                    val end = maxOf(departureIndex, arrivalIndex)
                    for (i in start + 1 until end) stations.add(names[i])
                }
                departureIndex >= 0 -> {
                    for (i in departureIndex + 1 until names.size) stations.add(names[i])
                }
                arrivalIndex >= 0 -> {
                    for (i in 0 until arrivalIndex) stations.add(names[i])
                }
            }
        }
        stations.remove(departure)
        stations.remove(arrival)
        return stations.toList()
    }

    private fun riskRank(transferTrain: TransferTrain): Int = when (transferTrain.risk) {
        com.railway.ticketsystem.model.TransferRisk.STEADY -> 0
        com.railway.ticketsystem.model.TransferRisk.TIGHT -> 1
        com.railway.ticketsystem.model.TransferRisk.NOT_RECOMMENDED -> 2
    }

    private fun transferTime(firstTrain: Train, secondTrain: Train): Int {
        var minutes = parseTimeToMinutes(secondTrain.departureTime) - parseTimeToMinutes(firstTrain.arrivalTime)
        if (minutes < 0) minutes += 24 * 60
        return minutes
    }

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun totalDuration(firstDuration: String, secondDuration: String, transferMinutes: Int): String {
        val total = parseDurationToMinutes(firstDuration) + parseDurationToMinutes(secondDuration) + transferMinutes
        val hours = total / 60
        val minutes = total % 60
        return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分"
    }

    internal fun parseDurationToMinutes(duration: String): Int {
        val value = duration.replace("分钟", "").trim()
        return try {
            if (value.contains("小时")) {
                val parts = value.split("小时")
                val hours = parts[0].trim().toIntOrNull() ?: 0
                val minutes = parts.getOrNull(1)?.replace("分", "")?.trim()?.toIntOrNull() ?: 0
                hours * 60 + minutes
            } else {
                value.replace("分", "").trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
