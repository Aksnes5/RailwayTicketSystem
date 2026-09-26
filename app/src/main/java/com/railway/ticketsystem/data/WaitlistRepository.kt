package com.railway.ticketsystem.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.receiver.WaitlistEvaluationReceiver
import com.railway.ticketsystem.model.WaitlistRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class WaitlistRepository(private val context: Context) {
    private val prefs: SharedPreferences = SecurePreferences.open(context, "secure_waitlist_data", "waitlist_data")
    private val gson = Gson()

    companion object {
        private const val REQUESTS_KEY = "waitlist_requests"
        private val waitlistLock = Any()
    }

    fun submit(order: Order): WaitlistRequest? {
        val request = synchronized(waitlistLock) {
            val requests = readRequests()
            val duplicated = requests.any { candidate ->
                candidate.userId == order.userId && candidate.status == "候补中" &&
                    isSameSeat(candidate.requestedOrder, order)
            }
            if (duplicated) return@synchronized null

            val probability = Random.nextInt(45, 91)
            val candidate = WaitlistRequest(
                id = "WAIT_${System.currentTimeMillis()}",
                userId = order.userId,
                requestedOrder = order,
                status = "候补中",
                createTime = now(),
                successProbability = probability,
                evaluationTime = evaluationTimeFor(order)
            )
            if (persist(requests + candidate)) candidate else null
        }
        request?.let(::scheduleEvaluation)
        request?.let { submitted ->
            MessageRepository(context).add(
                submitted.userId, MessageRepository.WAITLIST, "候补订单已提交",
                "${submitted.requestedOrder.trainNumber} ${submitted.requestedOrder.seatType} 候补成功，兑现概率 ${submitted.successProbability}%。",
                submitted.requestedOrder.id,
                eventKey = "waitlist_submitted:${submitted.id}",
                relatedWaitlistId = submitted.id
            )
            MembershipRepository(context).trackEvent(submitted.userId, MembershipRepository.EVENT_WAITLIST)
        }
        return request
    }

    fun getRequestsByUserId(userId: String): List<WaitlistRequest> = synchronized(waitlistLock) {
        readRequests().filter { it.userId == userId }.sortedByDescending { it.createTime }
    }

    fun getPendingRequestsFor(trainNumber: String, departureDate: String, seatType: String): List<WaitlistRequest> = synchronized(waitlistLock) {
        readRequests().filter {
            it.status == "候补中" &&
            it.requestedOrder.trainNumber == trainNumber &&
            it.requestedOrder.departureDate == departureDate &&
            it.requestedOrder.seatType == seatType
        }.sortedBy { it.createTime }
    }

    fun fulfillDirectly(request: WaitlistRequest, orderRepository: OrderRepository): WaitlistRequest? {
        val fulfilledOrder = reserveCompatibleSeat(request, orderRepository) ?: return null
        val result = synchronized(waitlistLock) {
            val requests = readRequests().toMutableList()
            val index = requests.indexOfFirst { it.id == request.id && it.status == "候补中" }
            if (index == -1) return@synchronized null
            val updated = requests[index].copy(
                status = "已兑现",
                fulfilledOrderId = fulfilledOrder.id,
                fulfilledTime = now(),
                resultMessage = "系统自动捕获退票余票并成功兑现"
            )
            requests[index] = updated
            if (persist(requests)) updated else null
        }
        if (result != null) {
            cancelEvaluation(request)
            UserRepository(context).adjustPointsOnce(
                request.userId,
                PointsPolicy.fromAmount(fulfilledOrder.finalPrice),
                "waitlist_payment:${request.id}"
            )
            val msg = MessageRepository(context).add(
                request.userId, MessageRepository.WAITLIST, "候补车票自动兑现成功！",
                "系统检测到 ${fulfilledOrder.trainNumber} ${fulfilledOrder.seatType} 释放余票，已自动为您出票（${fulfilledOrder.seatInfo}）并加入我的行程！",
                fulfilledOrder.id,
                eventKey = "waitlist_auto_fulfilled:${request.id}",
                relatedWaitlistId = request.id
            )
            if (msg != null) {
                LocalNotifications.post(context, msg)
            }
            TravelReminderScheduler.schedule(context, fulfilledOrder)
        }
        return result
    }

    fun getPendingCount(userId: String): Int = getRequestsByUserId(userId).count { it.status == "候补中" }

    fun hasPendingRequestFor(order: Order): Boolean = synchronized(waitlistLock) {
        readRequests().any { it.status == "候补中" && isSameSeat(it.requestedOrder, order) }
    }

    fun cancel(requestId: String, userId: String): Boolean {
        val cancelled = synchronized(waitlistLock) {
            val requests = readRequests().toMutableList()
            val index = requests.indexOfFirst { it.id == requestId && it.userId == userId && it.status == "候补中" }
            if (index == -1) return@synchronized null
            val request = requests[index]
            requests[index] = request.copy(status = "已终止", resultMessage = "用户已主动终止候补")
            if (persist(requests)) request else null
        }
        cancelled?.let(::cancelEvaluation)
        cancelled?.let { request ->
            MessageRepository(context).add(
                request.userId,
                MessageRepository.WAITLIST,
                "候补订单已终止",
                "已终止 ${request.requestedOrder.trainNumber} ${request.requestedOrder.seatType} 候补。",
                eventKey = "waitlist_cancelled:${request.id}",
                relatedWaitlistId = request.id
            )
        }
        return cancelled != null
    }

    /** Processes missed alarms whenever the app is opened again. */
    fun processDueRequests(orderRepository: OrderRepository) {
        val dueIds = synchronized(waitlistLock) {
            readRequests().filter { it.status == "候补中" && it.evaluationTime <= System.currentTimeMillis() }
                .map { it.id }
        }
        dueIds.forEach { processRequest(it, orderRepository) }
    }

    /** Rebuilds future alarms and catches up evaluations missed while the app was stopped. */
    private fun recoverLegacy() {
        val orderRepository = OrderRepository(context)
        processDueRequests(orderRepository)
        synchronized(waitlistLock) {
            readRequests().filter { it.status == "候补中" }.forEach(::scheduleEvaluation)
        }
    }

    /** Replays overdue requests and recreates every still-pending alarm after process restarts. */
    fun recover(orderRepository: OrderRepository = OrderRepository(context)) {
        processDueRequests(orderRepository)
        val pendingRequests = synchronized(waitlistLock) {
            readRequests().filter { it.status == "候补中" }
        }
        pendingRequests.forEach(::scheduleEvaluation)
    }

    /** Resolves one request once, using the probability stored at submission time. */
    fun processRequest(requestId: String, orderRepository: OrderRepository): WaitlistRequest? {
        val request = synchronized(waitlistLock) {
            readRequests().firstOrNull { it.id == requestId && it.status == "候补中" }
        } ?: return null
        if (request.evaluationTime > System.currentTimeMillis()) return request

        val won = Random.nextInt(100) < request.successProbability.coerceIn(0, 100)
        if (!won) return markResult(request, "兑现失败", "本次候补未兑现，可重新查询其他车次")

        val fulfilledOrder = reserveCompatibleSeat(request, orderRepository)
            ?: return markResult(request, "兑现失败", "本次候补未匹配到可用座位")
        val result = synchronized(waitlistLock) {
            val requests = readRequests().toMutableList()
            val index = requests.indexOfFirst { it.id == request.id && it.status == "候补中" }
            if (index == -1) return@synchronized null
            val updated = requests[index].copy(
                status = "已兑现",
                fulfilledOrderId = fulfilledOrder.id,
                fulfilledTime = now(),
                resultMessage = "候补已成功兑现，车票已加入我的行程"
            )
            requests[index] = updated
            if (persist(requests)) updated else null
        }
        if (result != null) {
            UserRepository(context).adjustPointsOnce(
                request.userId,
                PointsPolicy.fromAmount(fulfilledOrder.finalPrice),
                "waitlist_payment:${request.id}"
            )
            MessageRepository(context).add(
                request.userId, MessageRepository.WAITLIST, "候补兑现成功",
                "${fulfilledOrder.trainNumber} ${fulfilledOrder.seatInfo} 已加入我的行程，电子客票凭证已生成。",
                fulfilledOrder.id,
                eventKey = "waitlist_fulfilled:${request.id}",
                relatedWaitlistId = request.id
            )
            TravelReminderScheduler.schedule(context, fulfilledOrder)
        }
        return result
    }

    private fun reserveCompatibleSeat(request: WaitlistRequest, orderRepository: OrderRepository): Order? {
        val template = request.requestedOrder
        val cars = (1..16).map { it.toString() }
        val deterministicOrderId = "ORDER_WAIT_${request.id}"
        orderRepository.getOrderById(deterministicOrderId, request.userId)?.let { return it }
        val seats = when (template.seatType) {
            "一等座" -> listOf("A", "B", "D", "F")
            "商务座" -> listOf("A", "F")
            else -> listOf("A", "B", "C", "D", "F")
        }
        cars.forEach { car ->
            (1..20).forEach { row ->
                seats.forEach { letter ->
                    val seatNumber = "%02d%s".format(row, letter)
                    val order = template.copy(
                        id = deterministicOrderId,
                        carNumber = car,
                        seatNumber = seatNumber,
                        status = "已支付",
                        createTime = now(),
                        payTime = now()
                    )
                    if (orderRepository.saveOrder(order)) {
                        SeatInventoryRepository(context).consumeWaitlistSeat(template)
                        return order
                    }
                }
            }
                    orderRepository.getOrderById(deterministicOrderId, request.userId)?.let { return it }
        }
        return null
    }

    private fun markResult(request: WaitlistRequest, status: String, message: String): WaitlistRequest? = synchronized(waitlistLock) {
        val requests = readRequests().toMutableList()
        val index = requests.indexOfFirst { it.id == request.id && it.status == "候补中" }
        if (index == -1) return@synchronized null
        val updated = requests[index].copy(status = status, fulfilledTime = now(), resultMessage = message)
        requests[index] = updated
        val saved = if (persist(requests)) updated else null
        if (saved != null) {
            MessageRepository(context).add(
                saved.userId,
                MessageRepository.WAITLIST,
                if (status == "兑现失败") "候补兑现失败" else "候补订单状态更新",
                message,
                saved.requestedOrder.id,
                eventKey = "waitlist_result:${saved.id}:$status",
                relatedWaitlistId = saved.id
            )
        }
        saved
    }

    private fun scheduleEvaluation(request: WaitlistRequest) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WaitlistEvaluationReceiver::class.java).putExtra("waitlist_request_id", request.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, request.evaluationTime, pendingIntent)
    }

    private fun cancelEvaluation(request: WaitlistRequest) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WaitlistEvaluationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun evaluationTimeFor(order: Order): Long {
        val departure = runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply { isLenient = false }
                .parse("${order.departureDate} ${order.departureTime}")
        }.getOrNull()
        val now = System.currentTimeMillis()
        val departureTime = departure?.time ?: return now + 60_000L
        val earliest = departureTime - 2L * 24 * 60 * 60_000L
        val latest = departureTime - 60L * 60_000L
        val lowerBound = maxOf(now + 60_000L, earliest)
        return if (latest <= lowerBound) {
            lowerBound
        } else {
            Random.nextLong(lowerBound, latest + 1L)
        }
    }

    private fun readRequests(): List<WaitlistRequest> {
        val json = prefs.getString(REQUESTS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<WaitlistRequest>>() {}.type
        return runCatching { gson.fromJson<List<WaitlistRequest>>(json, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private fun persist(requests: List<WaitlistRequest>): Boolean = prefs.edit()
        .putString(REQUESTS_KEY, gson.toJson(requests))
        .commit()

    private fun isSameSeat(first: Order, second: Order): Boolean =
        first.trainNumber == second.trainNumber &&
            first.departureDate == second.departureDate &&
            first.seatType == second.seatType

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
}
