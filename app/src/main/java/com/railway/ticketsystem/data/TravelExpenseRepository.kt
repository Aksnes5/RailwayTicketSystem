package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

/** A normalized row lets ticket, hotel, meal and station-service payments share one ledger. */
data class TravelExpenseLine(
    val id: String,
    val category: String,
    val title: String,
    val occurredAt: String,
    val amountCents: Long,
    val referenceId: String
)

data class TravelExpenseSnapshot(
    val lines: List<TravelExpenseLine>,
    val categoryTotals: Map<String, Long>
) {
    val totalCents: Long get() = lines.sumOf { it.amountCents }
}

data class TravelExpenseReport(
    val id: String,
    val userId: String,
    val period: String,
    val totalCents: Long,
    val lineCount: Int,
    val status: String,
    val createdAt: String,
    val submittedAt: String? = null,
    val approvedAt: String? = null
)

/**
 * Local travel ledger. It never duplicates checkout data: every refresh derives the current
 * costs directly from the durable ticket, hotel, meal and station-service repositories.
 */
class TravelExpenseRepository(private val context: Context) {
    private val prefs = SecurePreferences.open(context.applicationContext, "secure_travel_expense_reports", "travel_expense_reports")
    private val gson = Gson()

    fun snapshot(userId: String): TravelExpenseSnapshot {
        val lines = buildList {
            OrderRepository(context).getOrdersByUserId(userId)
                .filter { it.status == "已支付" || it.status == "已完成" }
                .forEach { add(it.toExpense()) }
            HotelReservationRepository(context).getByUser(userId).forEach { hotel ->
                add(TravelExpenseLine(hotel.id, "酒店", "${hotel.hotelName} · ${hotel.roomName}", hotel.createdAt,
                    hotel.totalPrice.toLong() * 100L, hotel.id))
            }
            MealOrderRepository(context).getByUser(userId)
                .filter { it.status != "已取消" }
                .forEach { meal ->
                    add(TravelExpenseLine(meal.id, "餐饮", "${meal.trainNumber} · 车上餐饮", meal.createdAt,
                        meal.totalCents, meal.ticketOrderId))
                }
            StationServiceRepository(context).getByUser(userId)
                .filter { it.amountCents > 0L && it.status != "已取消" }
                .forEach { service ->
                    add(TravelExpenseLine(service.id, "车站服务", "${service.station} · ${service.serviceType}",
                        service.createdAt, service.amountCents, service.ticketOrderId ?: service.id))
                }
        }.sortedByDescending { it.occurredAt }
        return TravelExpenseSnapshot(lines, lines.groupBy { it.category }.mapValues { (_, value) -> value.sumOf { it.amountCents } })
    }

    fun getByUser(userId: String): List<TravelExpenseReport> = read()
        .filter { it.userId == userId }
        .sortedByDescending { it.createdAt }

    fun createReport(userId: String, snapshot: TravelExpenseSnapshot, period: String = currentPeriod()): TravelExpenseReport? {
        if (snapshot.lines.isEmpty()) return null
        val report = TravelExpenseReport(
            id = "EXP_" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
            userId = userId,
            period = period,
            totalCents = snapshot.totalCents,
            lineCount = snapshot.lines.size,
            status = "草稿",
            createdAt = timestamp()
        )
        return if (replace(report)) report else null
    }

    fun submit(report: TravelExpenseReport): TravelExpenseReport? {
        if (report.status != "草稿") return null
        val updated = report.copy(status = "审批中", submittedAt = timestamp())
        return if (replace(updated)) updated else null
    }

    fun approve(report: TravelExpenseReport): TravelExpenseReport? {
        if (report.status != "审批中") return null
        val updated = report.copy(status = "已通过", approvedAt = timestamp())
        return if (replace(updated)) updated else null
    }

    fun exportText(snapshot: TravelExpenseSnapshot, report: TravelExpenseReport?): String = buildString {
        append("铁路出行费用清单\n")
        append("生成时间：").append(timestamp()).append("\n")
        report?.let { append("报销单：").append(it.id).append(" · ").append(it.status).append("\n") }
        append("\n")
        snapshot.lines.forEach { line ->
            append(line.occurredAt).append("  ").append(line.category).append("\n")
            append(line.title).append("  ¥").append(money(line.amountCents)).append("\n\n")
        }
        append("合计：¥").append(money(snapshot.totalCents))
    }

    private fun Order.toExpense(): TravelExpenseLine = TravelExpenseLine(
        id = id,
        category = "火车票",
        title = "$trainNumber · $departureStation → $arrivalStation",
        occurredAt = payTime ?: createTime,
        amountCents = (finalPrice * 100.0).roundToLong(),
        referenceId = id
    )

    private fun replace(report: TravelExpenseReport): Boolean {
        val all = read().filterNot { it.id == report.id }
        return prefs.edit().putString(KEY, gson.toJson((all + report).takeLast(80))).commit()
    }

    private fun read(): List<TravelExpenseReport> {
        val type = object : TypeToken<List<TravelExpenseReport>>() {}.type
        return runCatching { gson.fromJson<List<TravelExpenseReport>>(prefs.getString(KEY, null), type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val KEY = "travel_expense_reports"
        fun money(cents: Long): String = String.format(Locale.CHINA, "%.2f", cents / 100.0)
        fun currentPeriod(): String = SimpleDateFormat("yyyy年MM月", Locale.CHINA).format(Date())
        private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
    }
}
