package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.TrainStopSchedule

class TrainStopScheduleAdapter(
    private var stops: List<TrainStopSchedule>,
    private var highlightedStartStation: String? = null,
    private var highlightedEndStation: String? = null
) : RecyclerView.Adapter<TrainStopScheduleAdapter.StopViewHolder>() {

    /** Lets a screen re-render without rebuilding the adapter and losing its view pool. */
    fun updateStops(
        newStops: List<TrainStopSchedule>,
        startStation: String? = highlightedStartStation,
        endStation: String? = highlightedEndStation
    ) {
        if (newStops == stops && startStation == highlightedStartStation && endStation == highlightedEndStation) return
        stops = newStops
        highlightedStartStation = startStation
        highlightedEndStation = endStation
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_train_stop_schedule, parent, false)
        return StopViewHolder(view)
    }

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        val stop = stops[position]
        val hasExplicitHighlights = !highlightedStartStation.isNullOrBlank() || !highlightedEndStation.isNullOrBlank()
        val isEndpoint = if (hasExplicitHighlights) {
            stop.stationName == highlightedStartStation || stop.stationName == highlightedEndStation
        } else {
            // Train-detail screens have no ticket segment, so retain their endpoint styling.
            position == 0 || position == stops.lastIndex
        }
        holder.bind(stop, isEndpoint)
    }

    override fun getItemCount(): Int = stops.size

    class StopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val station: TextView = itemView.findViewById(R.id.tvStopStation)
        private val arrival: TextView = itemView.findViewById(R.id.tvStopArrival)
        private val departure: TextView = itemView.findViewById(R.id.tvStopDeparture)
        private val dwell: TextView = itemView.findViewById(R.id.tvStopDwell)
        private val status: TextView = itemView.findViewById(R.id.tvStopStatus)

        fun bind(item: TrainStopSchedule, isEndpoint: Boolean) {
            station.text = item.stationName
            station.setTextColor(itemView.context.getColor(if (isEndpoint) R.color.railway_blue else R.color.text_primary))
            arrival.text = item.arrivalTime
            departure.text = item.departureTime
            dwell.text = item.dwellLabel
            status.text = item.operationalStatus
            val color = when {
                item.operationalStatus.startsWith("晚点") -> R.color.railway_red
                item.operationalStatus.startsWith("早点") -> R.color.green
                item.operationalStatus == "正点" -> R.color.railway_blue
                else -> R.color.text_secondary
            }
            status.setTextColor(itemView.context.getColor(color))
        }
    }
}
