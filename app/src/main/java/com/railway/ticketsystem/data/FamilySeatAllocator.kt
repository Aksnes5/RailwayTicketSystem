package com.railway.ticketsystem.data

/**
 * Produces deterministic, duplicate-free seats for a travelling party.  Inventory still makes
 * the final decision; this only describes the best physical arrangement in the selected coach.
 */
object FamilySeatAllocator {
    data class Plan(val seatNumbers: List<String>, val label: String) {
        val isSameRow: Boolean get() = label.startsWith("同排")
    }

    fun plan(seatType: String, preferredLetter: String, row: Int, passengerCount: Int): Plan {
        val count = passengerCount.coerceIn(1, 5)
        val letters = lettersFor(seatType)
        val start = letters.indexOf(preferredLetter).takeIf { it >= 0 } ?: 0
        val ordered = letters.drop(start) + letters.take(start)
        val seats = mutableListOf<String>()
        var remaining = count
        var rowOffset = 0
        while (remaining > 0) {
            val current = ordered.take(minOf(remaining, letters.size))
            val seatRow = ((row + rowOffset - 1) % 20) + 1
            seats += current.map { letter -> "%02d%s".format(seatRow, letter) }
            remaining -= current.size
            rowOffset++
        }
        val label = when {
            count <= letters.size -> "同排相邻座"
            rowOffset == 2 -> "相邻两排拆分（${letters.size}+${count - letters.size}）"
            else -> "相邻多排拆分"
        }
        return Plan(seats, label)
    }

    fun describe(seatType: String, passengerCount: Int): String {
        val capacity = lettersFor(seatType).size
        return when {
            passengerCount <= 1 -> "单人优先靠窗 / 靠走廊"
            passengerCount <= capacity -> "$passengerCount 人自动安排同排相邻座"
            else -> "$passengerCount 人将安排相邻两排，保持同车厢"
        }
    }

    private fun lettersFor(seatType: String): List<String> = when (seatType) {
        "商务座" -> listOf("A", "F")
        "一等座" -> listOf("A", "B", "D", "F")
        else -> listOf("A", "B", "C", "D", "F")
    }
}