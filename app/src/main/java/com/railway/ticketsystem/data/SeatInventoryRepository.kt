package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.Train
import kotlin.random.Random

/**
 * The single source of truth for a train's seat-class inventory in this local simulation.
 * A snapshot is generated once per train/date/class and then persisted so rendering never
 * changes the inventory and checkout reads exactly what the user saw.
 */
data class SeatAvailability(
    val trainNumber: String,
    val departureDate: String,
    val seatType: String,
    val availableSeats: Int
) {
    val requiresWaitlist: Boolean get() = availableSeats <= 0

    /** Railway-style display while preserving the actual count for booking checks. */
    val displayLabel: String
        get() = when {
            requiresWaitlist -> "候补"
            seatType == "商务座" && availableSeats > 10 -> "有票"
            seatType != "商务座" && availableSeats > 20 -> "有票"
            else -> "${availableSeats}张"
        }
}

class SeatInventoryRepository(context: Context) {
    private val prefs: SharedPreferences = SecurePreferences.open(
        context.applicationContext,
        "secure_seat_inventory_data",
        "seat_inventory_data"
    )
    private val gson = Gson()

    companion object {
        private const val INVENTORY_KEY = "seat_inventory"
        private val inventoryLock = Any()

        /**
         * 整份库存的内存副本，按应用共享。
         *
         * 原来每次读都要把整个 JSON 反序列化一遍，每次写都要整份序列化并同步落盘。中转列表
         * 排序一次要查上千次库存，光这一步实测三十多秒，界面看着像卡死。常驻内存后读变成
         * 查表，只有真正改动才写回，且改用 apply() 不再阻塞调用线程等磁盘。
         *
         * 必须是 companion：本类在多个 Activity 和 PaymentLifecycle 里各 new 一个，缓存
         * 放在实例上会让彼此读到陈旧副本。所有访问都在 inventoryLock 之下。
         */
        @Volatile
        private var cachedRecords: List<SeatAvailability>? = null
        val supportedSeatTypes = listOf("二等座", "一等座", "商务座", "硬座", "硬卧", "软卧", "无座")
    }

    fun getAvailabilities(train: Train, departureDate: String): Map<String, SeatAvailability> =
        synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            var changed = false
            val result = linkedMapOf<String, SeatAvailability>()
            supportedSeatTypesFor(train).forEach { seatType ->
                val index = records.indexOfFirst { matches(it, train.number, departureDate, seatType) }
                val availability = if (index >= 0) {
                    normalize(records[index]).also { normalized ->
                        if (normalized != records[index]) {
                            records[index] = normalized
                            changed = true
                        }
                    }
                } else {
                    SeatAvailability(
                        trainNumber = train.number,
                        departureDate = departureDate,
                        seatType = seatType,
                        availableSeats = generateInitialCount(seatType)
                    ).also {
                        records.add(it)
                        changed = true
                    }
                }
                result[seatType] = availability
            }
            // 读取阶段补出来的记录只更新内存副本，不写回磁盘。
            //
            // 写回要把整份库存序列化一遍，而 inventory 会随着读过的车次增长——中转列表
            // 排序一次要读上千次，等于上千次全量序列化加落盘，实测三十秒都跑不完。
            // 只在内存里补，同一进程内取值仍然稳定；真正的变更（下单、退票、候补）
            // 走 persist()，会把这批记录一并带上。
            if (changed) cachedRecords = records.toList()
            result
        }

    fun getAvailability(train: Train, departureDate: String, seatType: String): SeatAvailability =
        getAvailabilities(train, departureDate)[seatType]
            ?: SeatAvailability(train.number, departureDate, seatType, 0)

    /** Atomically decrements a seat-class snapshot when every requested ticket is available. */
    fun reserveSeats(train: Train, departureDate: String, seatType: String, count: Int): Boolean {
        if (count <= 0 || seatType !in supportedSeatTypesFor(train)) return false
        return synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, train.number, departureDate, seatType) }
            val availability = if (index >= 0) {
                normalize(records[index]).also { records[index] = it }
            } else {
                SeatAvailability(train.number, departureDate, seatType, generateInitialCount(seatType)).also(records::add)
            }
            if (availability.availableSeats < count) return@synchronized false
            records[records.indexOfFirst { matches(it, train.number, departureDate, seatType) }] =
                availability.copy(availableSeats = availability.availableSeats - count)
            persist(records)
        }
    }

    /**
     * Compensates a reservation when its surrounding order update fails.
     * A rollback is not a refund, so it deliberately creates no release receipt.
     */
    fun rollbackReservedSeats(train: Train, departureDate: String, seatType: String, count: Int = 1): Boolean {
        if (count <= 0 || seatType !in supportedSeatTypesFor(train)) return false
        return synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, train.number, departureDate, seatType) }
            if (index < 0) return@synchronized false
            records[index] = normalize(records[index].copy(
                availableSeats = records[index].availableSeats + count
            ))
            persist(records)
        }
    }

    /**
     * A successful candidate consumes a seat that became available outside this local user.
     * If the snapshot is still zero, the simulated release and immediate assignment cancel out.
     */
    fun consumeWaitlistSeat(order: Order): Boolean {
        if (order.seatType !in supportedSeatTypes) return false
        return synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            var index = records.indexOfFirst { matches(it, order.trainNumber, order.departureDate, order.seatType) }
            if (index < 0) {
                // 库存记录可能只存在于内存里（读路径不再落盘），不能把这当成"没有这个车次"。
                // 按 getAvailabilities 的方式补一条初始库存再走下面的核销。
                records.add(
                    SeatAvailability(
                        order.trainNumber,
                        order.departureDate,
                        order.seatType,
                        generateInitialCount(order.seatType)
                    )
                )
                index = records.lastIndex
            }
            if (records[index].availableSeats > 0) {
                records[index] = records[index].copy(availableSeats = records[index].availableSeats - 1)
                persist(records)
            } else true
        }
    }
    /** Restores one inventory unit after a paid ticket is successfully refunded. */
    fun releaseSeat(order: Order): Boolean {
        if (order.seatType !in supportedSeatTypes) return false
        return synchronized(inventoryLock) {
            // Order ids survive change-ticket operations. Scope the receipt to the exact
            // ticket snapshot so releasing the old seat cannot suppress a later refund.
            val receiptKey = listOf(
                "released_order",
                order.id,
                order.trainNumber,
                order.departureDate,
                order.seatType,
                order.carNumber,
                order.seatNumber
            ).joinToString("_")
            if (prefs.getBoolean(receiptKey, false)) return@synchronized true
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, order.trainNumber, order.departureDate, order.seatType) }
            if (index < 0) {
                // A legacy paid order may predate the persisted inventory snapshot.
                records.add(SeatAvailability(order.trainNumber, order.departureDate, order.seatType, 1))
            } else {
                records[index] = normalize(records[index].copy(availableSeats = records[index].availableSeats + 1))
            }
            // 这一处要连回执一起写，所以没走 persist()；但内存副本必须跟着更新，
            // 否则后续读取会拿到退票前的旧库存。
            cachedRecords = records.toList()
            prefs.edit()
                .putString(INVENTORY_KEY, gson.toJson(records))
                .putBoolean(receiptKey, true)
                .apply()
            true
        }
    }

    private fun generateInitialCount(seatType: String): Int = when (seatType) {
        "二等座" -> if (Random.nextInt(100) < 18) 0 else Random.nextInt(1, 101)
        "一等座" -> if (Random.nextInt(100) < 25) 0 else Random.nextInt(1, 61)
        "商务座" -> if (Random.nextInt(100) < 35) 0 else Random.nextInt(1, 21)
        "无座" -> if (Random.nextInt(100) < 15) 0 else Random.nextInt(1, 21)
        "硬座" -> if (Random.nextInt(100) < 12) 0 else Random.nextInt(1, 101)
        "硬卧" -> if (Random.nextInt(100) < 22) 0 else Random.nextInt(1, 61)
        "软卧" -> if (Random.nextInt(100) < 30) 0 else Random.nextInt(1, 31)
        else -> 0
    }

    private fun normalize(availability: SeatAvailability): SeatAvailability =
        if (availability.seatType == "无座" && availability.availableSeats > 20) {
            availability.copy(availableSeats = 20)
        } else {
            availability
        }

    private fun supportedSeatTypesFor(train: Train): List<String> =
        if (train.routeType == com.railway.ticketsystem.model.RouteType.CONVENTIONAL) {
            listOf("硬座", "硬卧", "软卧", "无座")
        } else if (train.number.trim().startsWith("D", ignoreCase = true)) {
            listOf("二等座", "一等座", "无座")
        } else {
            listOf("二等座", "一等座", "商务座", "无座")
        }

    private fun matches(
        record: SeatAvailability,
        trainNumber: String,
        departureDate: String,
        seatType: String
    ): Boolean =
        record.trainNumber == trainNumber &&
            record.departureDate == departureDate &&
            record.seatType == seatType

    /** 调用方一律 toMutableList() 后再改，所以这里返回的共享副本不会被就地篡改。 */
    private fun readRecords(): List<SeatAvailability> =
        cachedRecords ?: loadRecords().also { cachedRecords = it }

    private fun loadRecords(): List<SeatAvailability> {
        val json = prefs.getString(INVENTORY_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<SeatAvailability>>() {}.type
        return runCatching { gson.fromJson<List<SeatAvailability>>(json, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private fun persist(records: List<SeatAvailability>): Boolean {
        cachedRecords = records.toList()
        prefs.edit()
            .putString(INVENTORY_KEY, gson.toJson(records))
            .apply()
        return true
    }
}
