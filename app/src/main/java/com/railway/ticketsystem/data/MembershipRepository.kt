package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PointLedgerEntry(
    val id: String,
    val userId: String,
    val eventKey: String,
    val title: String,
    val delta: Int,
    val createdAt: String
)

data class WalletLedgerEntry(
    val id: String,
    val userId: String,
    val title: String,
    val amountCents: Long,
    val balanceCents: Long,
    val createdAt: String
)

data class MemberCoupon(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val description: String,
    val costPoints: Int,
    val createdAt: String,
    val status: String = "未使用"
)

data class MembershipTask(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val progress: Int,
    val rewardPoints: Int,
    val claimed: Boolean
) {
    val completed: Boolean get() = progress >= target
}

data class MemberGrowthProfile(
    val checkInStreak: Int,
    val stationFootprint: List<String>,
    val badgeTitle: String
) {
    val summary: String
        get() = "连续签到 $checkInStreak 天 · 已点亮 ${stationFootprint.size} 站 · $badgeTitle"
}

/** A durable, order-backed view of a member's railway activity for the current year. */
data class MemberTravelSnapshot(
    val year: Int,
    val tripCount: Int,
    val mileageKm: Int,
    val cities: List<String>,
    val frequentRoutes: List<String>,
    val carbonReductionKg: Int,
    val badges: List<MemberTravelBadge>,
    val checkInStreak: Int,
    val monthlyTrips: Int,
    val monthlyTarget: Int,
    val leaderboardRank: Int,
    val leaderboardPercentile: Int
)

data class MemberTravelBadge(
    val title: String,
    val description: String,
    val unlocked: Boolean
)

private data class TaskState(val id: String, val progress: Int = 0, val claimed: Boolean = false)
private data class TaskDefinition(val id: String, val title: String, val description: String, val target: Int, val rewardPoints: Int)
private data class CouponOffer(val title: String, val description: String, val cost: Int)

/** Persisted local member economy. Wallet recharge is a simulation and never makes a real charge. */
class MembershipRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = SecurePreferences.open(appContext, "secure_membership_data", "membership_data")
    private val gson = Gson()

    companion object {
        private val membershipLock = Any()
        const val COUPON_CASH = "cash"
        const val COUPON_UPGRADE = "upgrade"
        const val COUPON_CHANGE = "change"
        const val COUPON_LOUNGE = "lounge"
        const val EVENT_DAILY_CHECK_IN = "daily_check_in"
        const val EVENT_SEARCH = "search_route"
        const val EVENT_PAY_TICKET = "pay_ticket"
        const val EVENT_RECHARGE = "wallet_recharge"
        const val EVENT_WAITLIST = "join_waitlist"
        const val EVENT_CHANGE_TICKET = "change_ticket"
        const val EVENT_SERVICE_FEEDBACK = "service_feedback"
    }

    fun getWalletBalanceCents(userId: String): Long =
        prefs.getLong("wallet_balance_" + userId, 0L).coerceAtLeast(0L)

    fun getWalletBalanceText(userId: String): String =
        "¥" + String.format(Locale.CHINA, "%.2f", getWalletBalanceCents(userId) / 100.0)

    fun getPointLedger(userId: String): List<PointLedgerEntry> = synchronized(membershipLock) {
        readList<PointLedgerEntry>(pointLedgerKey(userId)).sortedByDescending { it.createdAt }.take(120)
    }

    fun getWalletLedger(userId: String): List<WalletLedgerEntry> = synchronized(membershipLock) {
        readList<WalletLedgerEntry>(walletLedgerKey(userId)).sortedByDescending { it.createdAt }.take(80)
    }

    fun getCoupons(userId: String): List<MemberCoupon> = synchronized(membershipLock) {
        readList<MemberCoupon>(couponKey(userId)).sortedByDescending { it.createdAt }
    }

    fun getAvailableCoupon(userId: String, type: String): MemberCoupon? = synchronized(membershipLock) {
        readList<MemberCoupon>(couponKey(userId))
            .sortedBy { it.createdAt }
            .firstOrNull { it.type == type && it.status == "未使用" }
    }

    fun getCouponUsedForUsage(userId: String, usageKey: String): MemberCoupon? = synchronized(membershipLock) {
        val couponId = prefs.getString("coupon_use_" + userId + "_" + usageKey, null) ?: return@synchronized null
        readList<MemberCoupon>(couponKey(userId)).firstOrNull { it.id == couponId && it.status == "已使用" }
    }

    /** Marks one coupon used by a durable usage key, so rotation/retry never consumes it twice. */
    fun consumeCoupon(userId: String, type: String, usageKey: String): MemberCoupon? = synchronized(membershipLock) {
        if (userId.isBlank() || type.isBlank() || usageKey.isBlank()) return@synchronized null
        val receipt = "coupon_use_" + userId + "_" + usageKey
        val coupons = readList<MemberCoupon>(couponKey(userId)).toMutableList()
        val usedId = prefs.getString(receipt, null)
        if (!usedId.isNullOrBlank()) return@synchronized coupons.firstOrNull { it.id == usedId }
        val index = coupons.indexOfFirst { it.type == type && it.status == "未使用" }
        if (index < 0) return@synchronized null
        val used = coupons[index].copy(status = "已使用")
        coupons[index] = used
        if (prefs.edit()
                .putString(couponKey(userId), gson.toJson(coupons))
                .putString(receipt, used.id)
                .commit()
        ) used else null
    }

    /** Reverts a coupon only when the payment/change operation that consumed it did not complete. */
    fun restoreCoupon(userId: String, couponId: String, usageKey: String): Boolean = synchronized(membershipLock) {
        val receipt = "coupon_use_" + userId + "_" + usageKey
        if (prefs.getString(receipt, null) != couponId) return@synchronized false
        val coupons = readList<MemberCoupon>(couponKey(userId)).toMutableList()
        val index = coupons.indexOfFirst { it.id == couponId && it.status == "已使用" }
        if (index < 0) return@synchronized false
        coupons[index] = coupons[index].copy(status = "未使用")
        prefs.edit()
            .putString(couponKey(userId), gson.toJson(coupons))
            .remove(receipt)
            .commit()
    }

    fun getTasks(userId: String): List<MembershipTask> {
        syncPassengerTask(userId)
        val states = synchronized(membershipLock) {
            readTaskStates(userId).associateBy { it.id }
        }
        return definitions().map { definition ->
            val state = states[definition.id] ?: TaskState(definition.id)
            MembershipTask(
                definition.id, definition.title, definition.description, definition.target,
                state.progress.coerceIn(0, definition.target), definition.rewardPoints, state.claimed
            )
        }
    }

    /** Called by UserRepository after every durable points mutation. */
    fun recordPointChange(userId: String, delta: Int, eventKey: String?) {
        if (userId.isBlank() || eventKey.isNullOrBlank() || delta == 0) return
        val saved = synchronized(membershipLock) {
            val receipt = "point_ledger_event_" + userId + "_" + eventKey
            if (prefs.getBoolean(receipt, false)) return@synchronized false
            val entries = readList<PointLedgerEntry>(pointLedgerKey(userId))
            val entry = PointLedgerEntry(
                "POINT_" + UUID.randomUUID(), userId, eventKey, pointTitle(eventKey, delta), delta, timestamp()
            )
            prefs.edit()
                .putString(pointLedgerKey(userId), gson.toJson((entries + entry).takeLast(300)))
                .putBoolean(receipt, true)
                .commit()
        }
        if (saved && delta > 0 && (eventKey.startsWith("payment:") || eventKey.startsWith("waitlist_payment:"))) {
            trackEvent(userId, EVENT_PAY_TICKET)
        }
    }

    fun rechargeWallet(userId: String, cents: Long): Boolean {
        if (userId.isBlank() || cents <= 0L) return false
        val saved = synchronized(membershipLock) {
            val balance = getWalletBalanceCents(userId) + cents
            appendWalletLocked(userId, "充值", cents, balance)
        }
        if (saved) trackEvent(userId, EVENT_RECHARGE)
        return saved
    }

    /** A batch receipt makes wallet payment safe against repeated taps. */
    fun payWithWallet(
        userId: String,
        batchId: String,
        cents: Long,
        title: String = "购买车票"
    ): Boolean {
        if (userId.isBlank() || batchId.isBlank() || cents <= 0L) return false
        return synchronized(membershipLock) {
            val receipt = "wallet_debit_" + userId + "_" + batchId
            if (prefs.getBoolean(receipt, false)) return@synchronized true
            val balance = getWalletBalanceCents(userId)
            if (balance < cents) return@synchronized false
            appendWalletLocked(userId, title + " · " + batchId, -cents, balance - cents, receipt)
        }
    }

    fun refundWalletPayment(userId: String, batchId: String, cents: Long): Boolean {
        if (userId.isBlank() || batchId.isBlank() || cents <= 0L) return false
        return synchronized(membershipLock) {
            val debit = "wallet_debit_" + userId + "_" + batchId
            val refund = "wallet_refund_" + userId + "_" + batchId
            if (!prefs.getBoolean(debit, false) || prefs.getBoolean(refund, false)) {
                return@synchronized prefs.getBoolean(refund, false)
            }
            appendWalletLocked(userId, "支付未完成，钱包退回", cents, getWalletBalanceCents(userId) + cents, refund)
        }
    }

    /** Credits a durable, named refund to the railway wallet exactly once. */
    fun creditWallet(userId: String, cents: Long, title: String, receiptKey: String): Boolean {
        if (userId.isBlank() || cents <= 0L || receiptKey.isBlank()) return false
        return synchronized(membershipLock) {
            val receipt = "wallet_credit_" + userId + "_" + receiptKey
            if (prefs.getBoolean(receipt, false)) return@synchronized true
            appendWalletLocked(userId, title, cents, getWalletBalanceCents(userId) + cents, receipt)
        }
    }

    fun checkIn(userId: String): Boolean {
        trackEvent(userId, EVENT_DAILY_CHECK_IN)
        if (!claimTask(userId, "daily_check_in")) return false
        val streak = recordCheckInStreak(userId)
        if (streak > 0 && streak % 7 == 0) {
            UserRepository(appContext).adjustPointsOnce(
                userId,
                20,
                "checkin_streak:" + userId + ":" + today()
            )
        }
        return true
    }

    /** Builds the member page's travel footprint from durable ticket records. */
    fun getGrowthProfile(userId: String): MemberGrowthProfile {
        val orders = OrderRepository(appContext).getOrdersByUserId(userId)
            .filter { it.status != "已取消" }
        val stations = orders.flatMap { listOf(it.departureStation, it.arrivalStation) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val paidTrips = orders.count { it.status == "已支付" || it.status == "已完成" }
        val badge = when {
            paidTrips >= 12 -> "铁路常旅客"
            paidTrips >= 5 -> "城市探索家"
            paidTrips >= 1 -> "首次出发"
            else -> "启程新星"
        }
        return MemberGrowthProfile(getCheckInStreak(userId), stations, badge)
    }

    /**
     * Aggregates the growth dashboard from persisted orders instead of producing a new
     * random profile on every render. A paid journey enters the annual account once its
     * travel day has arrived; completed journeys remain in the history permanently.
     */
    fun getTravelGrowthSnapshot(userId: String, memberPoints: Int): MemberTravelSnapshot {
        val today = today()
        val year = today.take(4).toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val monthPrefix = today.take(7)
        val travelled = OrderRepository(appContext).getOrdersByUserId(userId)
            .filter { order ->
                order.departureDate.startsWith(year.toString()) &&
                    (order.status == "已完成" || (order.status == "已支付" && order.departureDate <= today))
            }
        val mileage = travelled.sumOf(::estimateMileageKm)
        val cities = travelled
            .flatMap { listOf(it.departureStation, it.arrivalStation) }
            .map(::cityForStation)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val frequentRoutes = travelled
            .groupingBy { "${it.departureStation}—${it.arrivalStation}" }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .map { "${it.key} · ${it.value}次" }
        val carbon = (mileage * 0.115).toInt()
        val streak = getCheckInStreak(userId)
        val monthlyTrips = travelled.count { it.departureDate.startsWith(monthPrefix) }
        val target = 3
        val badges = listOf(
            MemberTravelBadge("启程新星", "完成首段铁路旅程", travelled.isNotEmpty()),
            MemberTravelBadge("千里奔赴", "年度铁路里程满 1,000 km", mileage >= 1_000),
            MemberTravelBadge("城市漫游者", "点亮 3 座出行城市", cities.size >= 3),
            MemberTravelBadge("绿色同行", "累计减少 50 kg 碳排放", carbon >= 50),
            MemberTravelBadge("签到不息", "连续签到 7 天", streak >= 7)
        )
        // The account has no network leaderboard. Keep the local ranking stable for one
        // member/year, while allowing points and actual trips to improve the displayed tier.
        val seed = kotlin.math.abs((userId + year).hashCode())
        val baseRank = 45 + seed % 160
        val rank = (baseRank - (memberPoints / 180) - (tripCountBonus(travelled.size, mileage))).coerceAtLeast(1)
        val percentile = (100 - rank / 3).coerceIn(35, 99)
        return MemberTravelSnapshot(
            year = year,
            tripCount = travelled.size,
            mileageKm = mileage,
            cities = cities,
            frequentRoutes = frequentRoutes,
            carbonReductionKg = carbon,
            badges = badges,
            checkInStreak = streak,
            monthlyTrips = monthlyTrips,
            monthlyTarget = target,
            leaderboardRank = rank,
            leaderboardPercentile = percentile
        )
    }

    fun getCheckInStreak(userId: String): Int =
        prefs.getInt("checkin_streak_" + userId, 0).coerceAtLeast(0)

    private fun estimateMileageKm(order: Order): Int {
        val duration = durationMinutes(order.timetableDuration).takeIf { it > 0 }
            ?: durationMinutesBetween(order.departureTime, order.arrivalTime)
        val prefix = order.trainNumber.trim().firstOrNull()?.uppercaseChar()
        val hourlySpeed = when (prefix) {
            'G', 'C' -> 270
            'D' -> 200
            else -> 105
        }
        return ((duration.coerceAtLeast(15) * hourlySpeed) / 60).coerceIn(40, 2_600)
    }

    private fun durationMinutes(value: String?): Int {
        val text = value.orEmpty()
        val hour = Regex("(\\d+)小时").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minute = Regex("(\\d+)(?:分钟|分)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }

    private fun durationMinutesBetween(departure: String, arrival: String): Int {
        fun toMinutes(value: String): Int {
            val parts = value.split(":")
            return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        var result = toMinutes(arrival) - toMinutes(departure)
        if (result <= 0) result += 24 * 60
        return result
    }

    private fun tripCountBonus(tripCount: Int, mileageKm: Int): Int =
        (tripCount * 2 + mileageKm / 800).coerceAtMost(35)

    private fun cityForStation(station: String): String {
        val normalized = station.trim()
        val aliases = linkedMapOf(
            "汉口" to "武汉", "武昌" to "武汉", "武汉" to "武汉",
            "北京" to "北京", "上海" to "上海", "天津" to "天津", "重庆" to "重庆",
            "广州" to "广州", "深圳" to "深圳", "成都" to "成都", "西安" to "西安",
            "郑州" to "郑州", "长沙" to "长沙", "杭州" to "杭州", "南京" to "南京",
            "合肥" to "合肥", "南昌" to "南昌", "济南" to "济南", "青岛" to "青岛",
            "福州" to "福州", "厦门" to "厦门", "南宁" to "南宁", "昆明" to "昆明",
            "贵阳" to "贵阳", "兰州" to "兰州", "西宁" to "西宁", "银川" to "银川",
            "乌鲁木齐" to "乌鲁木齐", "拉萨" to "拉萨", "呼和浩特" to "呼和浩特",
            "太原" to "太原", "石家庄" to "石家庄", "沈阳" to "沈阳", "长春" to "长春",
            "哈尔滨" to "哈尔滨", "宜昌" to "宜昌", "襄阳" to "襄阳", "洛阳" to "洛阳",
            "苏州" to "苏州", "无锡" to "无锡", "徐州" to "徐州", "宁波" to "宁波",
            "温州" to "温州", "大连" to "大连", "烟台" to "烟台", "海口" to "海口"
        )
        return aliases.entries.firstOrNull { normalized.startsWith(it.key) }?.value
            ?: normalized.removeSuffix("东").removeSuffix("西").removeSuffix("南").removeSuffix("北").ifBlank { normalized }
    }

    fun claimTask(userId: String, taskId: String): Boolean {
        val task = getTasks(userId).firstOrNull { it.id == taskId } ?: return false
        if (!task.completed || task.claimed) return false
        val eventKey = "task_reward:" + userId + ":" + today() + ":" + taskId
        if (!UserRepository(appContext).adjustPointsOnce(userId, task.rewardPoints, eventKey)) return false
        return synchronized(membershipLock) {
            val states = readTaskStates(userId).toMutableList()
            val index = states.indexOfFirst { it.id == taskId }
            if (index < 0) return@synchronized false
            states[index] = states[index].copy(claimed = true)
            persistTaskStates(userId, states)
        }
    }

    fun redeemCoupon(userId: String, type: String): MemberCoupon? {
        val offer = couponOffers()[type] ?: return null
        val coupon = MemberCoupon(
            "CPN_" + UUID.randomUUID(), userId, type, offer.title, offer.description, offer.cost, timestamp()
        )
        if (!UserRepository(appContext).adjustPointsOnce(userId, -offer.cost, "coupon_redeem:" + coupon.id)) return null
        val saved = synchronized(membershipLock) {
            val existing = readList<MemberCoupon>(couponKey(userId))
            prefs.edit().putString(couponKey(userId), gson.toJson((existing + coupon).takeLast(100))).commit()
        }
        return coupon.takeIf { saved }
    }

    fun trackEvent(userId: String, event: String, count: Int = 1) {
        if (userId.isBlank() || count <= 0) return
        val ids = when (event) {
            EVENT_DAILY_CHECK_IN -> listOf("daily_check_in")
            EVENT_SEARCH -> listOf("search_route")
            EVENT_PAY_TICKET -> listOf("pay_ticket")
            EVENT_RECHARGE -> listOf("wallet_recharge")
            EVENT_WAITLIST -> listOf("join_waitlist")
            EVENT_CHANGE_TICKET -> listOf("change_ticket")
            EVENT_SERVICE_FEEDBACK -> listOf("service_feedback")
            else -> emptyList()
        }
        if (ids.isEmpty()) return
        synchronized(membershipLock) {
            val definitions = definitions().associateBy { it.id }
            val states = readTaskStates(userId).toMutableList()
            ids.forEach { id ->
                val definition = definitions[id] ?: return@forEach
                val index = states.indexOfFirst { it.id == id }
                val old = if (index >= 0) states[index] else TaskState(id)
                val updated = old.copy(progress = (old.progress + count).coerceAtMost(definition.target))
                if (index >= 0) states[index] = updated else states.add(updated)
            }
            persistTaskStates(userId, states)
        }
    }

    private fun syncPassengerTask(userId: String) {
        if (PassengerRepository(appContext).getPassengersByUserId(userId).isEmpty()) return
        synchronized(membershipLock) {
            val states = readTaskStates(userId).toMutableList()
            val index = states.indexOfFirst { it.id == "add_passenger" }
            val old = if (index >= 0) states[index] else TaskState("add_passenger")
            if (old.progress < 1) {
                if (index >= 0) states[index] = old.copy(progress = 1) else states.add(old.copy(progress = 1))
                persistTaskStates(userId, states)
            }
        }
    }

    private fun appendWalletLocked(
        userId: String, title: String, amountCents: Long, balanceCents: Long, receipt: String? = null
    ): Boolean {
        val entries = readList<WalletLedgerEntry>(walletLedgerKey(userId))
        val entry = WalletLedgerEntry(
            "WALLET_" + UUID.randomUUID(), userId, title, amountCents, balanceCents, timestamp()
        )
        val editor = prefs.edit()
            .putLong("wallet_balance_" + userId, balanceCents)
            .putString(walletLedgerKey(userId), gson.toJson((entries + entry).takeLast(120)))
        if (receipt != null) editor.putBoolean(receipt, true)
        return editor.commit()
    }

    private fun pointTitle(eventKey: String, delta: Int): String = when {
        eventKey.startsWith("payment:") -> "购票获得积分"
        eventKey.startsWith("waitlist_payment:") -> "候补兑现获得积分"
        eventKey.startsWith("ticket_refund:") -> "退票扣减积分"
        eventKey.startsWith("itinerary_refund:") -> "中转换乘退票扣减积分"
        eventKey.startsWith("change_ticket_points:") && delta < 0 -> "改签扣减积分"
        eventKey.startsWith("change_ticket_points:") -> "改签返还积分"
        eventKey.startsWith("coupon_redeem:") -> "兑换会员权益券"
        eventKey.startsWith("task_reward:") -> "任务中心奖励"
        eventKey.startsWith("checkin_streak:") -> "连续签到加赠"
        eventKey.startsWith("service_feedback:") -> "服务评价奖励"
        else -> if (delta > 0) "会员积分奖励" else "会员积分扣减"
    }

    private fun definitions() = listOf(
        TaskDefinition("daily_check_in", "每日签到", "今日打开会员中心即可签到", 1, 10),
        TaskDefinition("search_route", "行程探索", "查询 3 次车次，解锁出行建议", 3, 18),
        TaskDefinition("add_passenger", "完善乘车人", "添加至少 1 位常用乘车人", 1, 20),
        TaskDefinition("wallet_recharge", "钱包体验官", "完成一次充值", 1, 12),
        TaskDefinition("pay_ticket", "完成一次购票", "支付一笔订单或候补兑现，积累会员成长", 1, 35),
        TaskDefinition("join_waitlist", "候补守望者", "提交 1 次候补，关注系统处理结果", 1, 16),
        TaskDefinition("change_ticket", "行程调度员", "完成 1 次改签，掌握灵活出行", 1, 22),
        TaskDefinition("service_feedback", "服务体验官", "完成 2 次车站或车厢服务评价", 2, 20)
    )

    private fun couponOffers() = mapOf(
        COUPON_CASH to CouponOffer("¥20 购票抵扣券", "支付页会自动抵扣一张；不足部分可用钱包或支付。", 220),
        COUPON_UPGRADE to CouponOffer("升座体验券", "升座改签时自动免除积分补差。", 450),
        COUPON_CHANGE to CouponOffer("免费改签券", "普通改签时自动免除积分补差。", 320),
        COUPON_LOUNGE to CouponOffer("高铁贵宾候车厅券", "出行当日可在权益包中出示使用，限单次有效。", 520)
    )

    private fun pointLedgerKey(userId: String) = "point_ledger_" + userId
    private fun walletLedgerKey(userId: String) = "wallet_ledger_" + userId
    private fun couponKey(userId: String) = "member_coupons_" + userId
    private fun taskKey(userId: String) = "member_tasks_" + userId + "_" + today()

    private inline fun <reified T> readList(key: String): List<T> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(json, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    private fun readTaskStates(userId: String): List<TaskState> = readList(taskKey(userId))
    private fun persistTaskStates(userId: String, states: List<TaskState>): Boolean =
        prefs.edit().putString(taskKey(userId), gson.toJson(states)).commit()

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())

    private fun recordCheckInStreak(userId: String): Int = synchronized(membershipLock) {
        val lastKey = "checkin_last_date_" + userId
        val streakKey = "checkin_streak_" + userId
        val today = today()
        val lastDate = prefs.getString(lastKey, null)
        val previousStreak = prefs.getInt(streakKey, 0).coerceAtLeast(0)
        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
            .let { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(it.time) }
        val nextStreak = when {
            lastDate == today -> previousStreak.coerceAtLeast(1)
            lastDate == yesterday -> previousStreak + 1
            else -> 1
        }
        prefs.edit().putString(lastKey, today).putInt(streakKey, nextStreak).commit()
        nextStreak
    }
}
