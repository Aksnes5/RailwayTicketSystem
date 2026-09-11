package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.Station
import java.nio.charset.Charset
import java.text.Collator
import java.util.Locale

/**
 * Flat, sectioned station directory. It avoids walking every group again for each row,
 * keeping long station lists smooth while preserving a direct A–Z jump target.
 */
class StationDirectoryAdapter(
    private val onStationClick: (Station) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val letter: Char) : Row()
        data class StationRow(val station: Station) : Row()
    }

    private val rows = mutableListOf<Row>()
    private val sectionPositions = mutableMapOf<Char, Int>()
    private val collator = Collator.getInstance(Locale.CHINA)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_STATION = 1
        private val gbk: Charset = Charset.forName("GBK")
        private val pinyinBoundaries = intArrayOf(
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787, 3106, 3212, 3472,
            3635, 3722, 3730, 3858, 4027, 4086, 4390, 4558, 4684, 4925, 5249
        )
        private val pinyinLetters = charArrayOf(
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
        )
    }

    fun updateStations(stations: List<Station>) {
        val grouped = stations
            .distinctBy { it.name }
            .sortedWith { first, second -> collator.compare(first.name, second.name) }
            .groupBy(::initialOf)
            .toSortedMap()
        rows.clear()
        sectionPositions.clear()
        grouped.forEach { (letter, sectionStations) ->
            sectionPositions[letter] = rows.size
            rows += Row.Header(letter)
            sectionStations.forEach { station -> rows += Row.StationRow(station) }
        }
        notifyDataSetChanged()
    }

    fun scrollToSection(letter: Char, recyclerView: RecyclerView) {
        val requested = letter.uppercaseChar()
        val position = sectionPositions[requested]
            ?: sectionPositions.entries.firstOrNull { it.key >= requested }?.value
            ?: sectionPositions.values.lastOrNull()
            ?: return
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.StationRow -> TYPE_STATION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_HEADER) R.layout.item_station_header else R.layout.item_station
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        } else {
            StationViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).bind(row.letter)
            is Row.StationRow -> (holder as StationViewHolder).bind(row.station)
        }
    }

    override fun getItemCount(): Int = rows.size

    private fun initialOf(station: Station): Char {
        val first = station.name.trim().firstOrNull() ?: return 'A'
        if (first.isAsciiLetter()) return first.uppercaseChar()

        // GBK code-point ranges map the first Chinese character to its pinyin initial.
        // The previous implementation used a Unicode-style calculation, which made
        // every Chinese station miss the ranges and fall into a "#" section.
        val bytes = first.toString().toByteArray(gbk)
        if (bytes.size < 2) return 'A'
        val gbkCode = ((bytes[0].toInt() and 0xFF) - 160) * 100 +
            ((bytes[1].toInt() and 0xFF) - 160)
        for (index in pinyinBoundaries.indices.reversed()) {
            if (gbkCode >= pinyinBoundaries[index]) return pinyinLetters[index]
        }
        return 'A'
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val letter: TextView = itemView.findViewById(R.id.tvLetter)
        fun bind(value: Char) {
            letter.text = value.toString()
        }
    }

    private inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvStationName)

        fun bind(station: Station) {
            name.text = station.name
            itemView.contentDescription = "选择车站 " + station.name
            itemView.setOnClickListener { onStationClick(station) }
        }
    }
}
