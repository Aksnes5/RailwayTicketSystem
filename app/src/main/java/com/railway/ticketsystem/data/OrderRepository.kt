package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Order

class OrderRepository internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(SecurePreferences.open(context, "secure_order_data", "order_data"))
    private val gson = Gson()
    
    companion object {
        private val orderLock = Any()
    }
    fun saveOrder(order: Order): Boolean = saveOrdersIfSeatsAvailable(listOf(order))

    /** Persists a complete itinerary only when none of its seats is already active. */
    fun saveOrdersIfSeatsAvailable(orders: List<Order>): Boolean {
        if (orders.isEmpty() || orders.map { it.id }.distinct().size != orders.size) return false
        if (orders.any { !it.finalPrice.isFinite() || it.finalPrice < 0 }) return false
        if (orders.any { it.status == "待支付" } && (
            orders.any { it.status != "待支付" } || orders.map { it.userId }.distinct().size != 1 ||
            orders.map { it.paymentBatchId }.distinct().size != 1 || orders.first().paymentBatchId.isNullOrBlank() ||
            orders.map { it.paymentDeadlineMillis }.distinct().size != 1 ||
            orders.first().paymentDeadlineMillis <= System.currentTimeMillis()
        )) return false
        return synchronized(orderLock) {
            val current = readOrders()
            if (orders.any { candidate -> current.any { it.id == candidate.id } }) return@synchronized false

            val pending = mutableListOf<Order>()
            orders.forEach { order ->
                if (SeatReservationPolicy.conflicts(current + pending, order)) return@synchronized false
                pending.add(order)
            }
            persist(current + pending)
        }
    }
    
    fun getAllOrders(): List<Order> = synchronized(orderLock) { readOrders() }
    
    fun getOrdersByUserId(userId: String): List<Order> {
        return getAllOrders().filter { it.userId == userId }
    }
    
    fun getOrdersByItineraryId(itineraryId: String, userId: String): List<Order> =
        getAllOrders().filter { it.itineraryId == itineraryId && it.userId == userId }

    fun getOrdersByPaymentBatchId(paymentBatchId: String): List<Order> =
        getAllOrders().filter { it.paymentBatchId == paymentBatchId }

    fun getOrdersByPaymentBatchId(paymentBatchId: String, userId: String): List<Order> =
        getAllOrders().filter { it.paymentBatchId == paymentBatchId && it.userId == userId }

    /** Completes every seat held by one checkout as a single persisted transition. */
    fun payPendingPaymentBatch(paymentBatchId: String, userId: String, payTime: String, now: Long = System.currentTimeMillis()): List<Order>? = synchronized(orderLock) {
        val stored = readOrders().toMutableList()
        val indexes = stored.indices.filter { stored[it].paymentBatchId == paymentBatchId }
        if (indexes.isEmpty() || indexes.any {
            stored[it].userId != userId || stored[it].status != "待支付" || stored[it].paymentDeadlineMillis <= now
        }) return@synchronized null
        val paid = indexes.map { index -> stored[index].copy(status = "已支付", payTime = payTime, paymentEffectsPending = true) }
        indexes.forEachIndexed { offset, index -> stored[index] = paid[offset] }
        if (persist(stored)) paid else null
    }

    /** Cancels every still-pending seat in a checkout. Callers release inventory after success. */
    fun cancelPendingPaymentBatch(paymentBatchId: String, userId: String, reason: String = "用户取消"): List<Order>? = synchronized(orderLock) {
        val stored = readOrders().toMutableList()
        val indexes = stored.indices.filter { stored[it].paymentBatchId == paymentBatchId }
        if (indexes.isEmpty() || indexes.any { stored[it].userId != userId || stored[it].status != "待支付" }) return@synchronized null
        val cancelled = indexes.map { stored[it] }
        indexes.forEach { index -> stored[index] = stored[index].copy(status = "已取消", cancelReason = reason, inventoryReleasePending = true) }
        if (persist(stored)) cancelled else null
    }

    /** Sweeps overdue holds when an alarm was delayed or the app was not running. */
    fun expirePendingPayments(now: Long = System.currentTimeMillis()): List<Order> = synchronized(orderLock) {
        val stored = readOrders().toMutableList()
        val expiredBatches = stored.filter { it.status == "待支付" && it.paymentDeadlineMillis <= now }
            .map { it.userId to it.paymentBatchId }.toSet()
        val indexes = stored.indices.filter {
            stored[it].status == "待支付" && (stored[it].userId to stored[it].paymentBatchId) in expiredBatches
        }
        if (indexes.isEmpty()) return@synchronized emptyList()
        val expired = indexes.map { stored[it] }
        indexes.forEach { index -> stored[index] = stored[index].copy(status = "已取消", cancelReason = "支付超时", inventoryReleasePending = true) }
        if (persist(stored)) expired else emptyList()
    }

    /**
     * Archives every paid trip whose scheduled arrival has passed, in one persisted update.
     * Nothing else promotes an order to "已完成", so without this sweep a finished journey
     * would never reach the history list or become eligible for an invoice.
     */
    fun completeArrivedOrders(now: Long = System.currentTimeMillis()): List<Order> = synchronized(orderLock) {
        val stored = readOrders().toMutableList()
        val indexes = stored.indices.filter { index ->
            stored[index].status == "已支付" &&
                TravelAssistant.arrivalMillis(stored[index])?.let { it <= now } == true
        }
        if (indexes.isEmpty()) return@synchronized emptyList()
        val completed = indexes.map { stored[it] }
        indexes.forEach { index -> stored[index] = stored[index].copy(status = "已完成") }
        if (persist(stored)) completed else emptyList()
    }

    /** Cancels every still-active leg of a linked transfer itinerary in one persisted update. */
    fun cancelItinerary(itineraryId: String, userId: String): List<Order>? = synchronized(orderLock) {
        val orders = readOrders().toMutableList()
        val activeIndexes = orders.indices.filter { index ->
            orders[index].itineraryId == itineraryId &&
                orders[index].userId == userId && orders[index].status == "已支付"
        }
        if (activeIndexes.isEmpty()) return@synchronized null
        val cancelled = activeIndexes.map { orders[it] }
        activeIndexes.forEach { index -> orders[index] = orders[index].copy(status = "已取消", cancelReason = "整组退票", inventoryReleasePending = true) }
        if (persist(orders)) cancelled else null
    }

    fun getOrderById(orderId: String): Order? = getAllOrders().find { it.id == orderId }
    fun getOrderById(orderId: String, userId: String): Order? = getAllOrders().find { it.id == orderId && it.userId == userId }
    
    fun updateOrderStatus(orderId: String, status: String): Boolean =
        updateOrderStatusInternal({ it.id == orderId }, status)

    fun updateOrderStatus(orderId: String, userId: String, status: String): Boolean =
        updateOrderStatusInternal({ it.id == orderId && it.userId == userId }, status)

    private fun updateOrderStatusInternal(matches: (Order) -> Boolean, status: String): Boolean {
        val outcome = synchronized(orderLock) {
            val orders = readOrders().toMutableList()
            val index = orders.indexOfFirst(matches)
            if (index == -1) return@synchronized false to null
            val original = orders[index]
            if (original.status != "已支付" || status !in setOf("已取消", "已完成")) return@synchronized false to null
            val updated = original.copy(
                status = status, inventoryReleasePending = status == "已取消",
                cancelReason = if (status == "已取消") "用户退票" else original.cancelReason
            )
            orders[index] = updated
            val saved = persist(orders)
            val released = if (saved && SeatReservationPolicy.isActive(original) && !SeatReservationPolicy.isActive(updated)) {
                updated
            } else {
                null
            }
            saved to released
        }
        return outcome.first
    }
    
    fun deleteOrder(orderId: String): Boolean = updateOrderStatus(orderId, "已取消")
    
    fun updateOrder(updatedOrder: Order): Boolean =
        replaceOrderIfSeatAvailable(updatedOrder.id, updatedOrder.userId, updatedOrder)

    fun replaceOrderIfSeatAvailable(orderId: String, userId: String, updatedOrder: Order, expectedOriginal: Order? = null): Boolean = synchronized(orderLock) {
        val orders = readOrders().toMutableList()
        val index = orders.indexOfFirst { it.id == orderId && it.userId == userId }
        if (index == -1 || updatedOrder.id != orderId || updatedOrder.userId != userId ||
            orders[index].status != "已支付" || updatedOrder.status != "已支付" ||
            (expectedOriginal != null && orders[index] != expectedOriginal)) return@synchronized false
        val withoutOriginal = orders.filterIndexed { currentIndex, _ -> currentIndex != index }
        if (SeatReservationPolicy.conflicts(withoutOriginal, updatedOrder)) return@synchronized false
        orders[index] = updatedOrder
        persist(orders)
    }

    fun acknowledgeInventoryRelease(orderId: String): Boolean = synchronized(orderLock) {
        persist(readOrders().map { if (it.id == orderId) it.copy(inventoryReleasePending = false) else it })
    }

    fun acknowledgePaymentEffects(batchId: String, userId: String): Boolean = synchronized(orderLock) {
        persist(readOrders().map {
            if (it.paymentBatchId == batchId && it.userId == userId) it.copy(paymentEffectsPending = false) else it
        })
    }

    private fun readOrders(): List<Order> {
        val json = prefs.getString("orders", null) ?: return emptyList()
        val type = object : TypeToken<List<Order>>() {}.type
        val raw = runCatching { gson.fromJson<List<Order>>(json, type) ?: emptyList() }.getOrDefault(emptyList())
        // Gson skips constructor defaults; preserve the original creation clock for legacy holds.
        return raw.map { order ->
            if (order.status != "待支付") order else order.copy(
                paymentBatchId = order.paymentBatchId?.takeIf { it.isNotBlank() } ?: order.itineraryId ?: order.groupId ?: order.id,
                paymentDeadlineMillis = order.paymentDeadlineMillis.takeIf { it > 0 } ?: runCatching {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).apply { isLenient = false }
                        .parse(order.createTime)!!.time + 15L * 60_000L
                }.getOrDefault(1L)
            )
        }
    }

    private fun persist(orders: List<Order>): Boolean = prefs.edit()
        .putString("orders", gson.toJson(orders))
        .commit()
}
