package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.data.SeatAvailability
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.RouteType

class TrainResultAdapter(
    private val seatInventoryRepository: SeatInventoryRepository,
    private val departureDateProvider: () -> String,
    private val onTrainClick: (Train) -> Unit
) : RecyclerView.Adapter<TrainResultAdapter.TrainResultViewHolder>() {

    private var trains = mutableListOf<Train>()

    fun updateTrains(newTrains: List<Train>) {
        trains.clear()
        trains.addAll(newTrains)
        notifyDataSetChanged()
    }

    fun getCurrentTrains(): List<Train> = trains

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrainResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_train_result, parent, false)
        return TrainResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrainResultViewHolder, position: Int) {
        holder.bind(trains[position])
    }

    override fun getItemCount(): Int = trains.size

    inner class TrainResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDepartureTime: TextView = itemView.findViewById(R.id.tvDepartureTime)
        private val tvArrivalTime: TextView = itemView.findViewById(R.id.tvArrivalTime)
        private val tvDepartureStation: TextView = itemView.findViewById(R.id.tvDepartureStation)
        private val tvArrivalStation: TextView = itemView.findViewById(R.id.tvArrivalStation)
        private val tvTrainNumber: TextView = itemView.findViewById(R.id.tvTrainNumber)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val llSeatInfo: LinearLayout = itemView.findViewById(R.id.llSeatInfo)

        fun bind(train: Train) {
            tvDepartureTime.text = train.departureTime
            tvArrivalTime.text = train.arrivalTime
            tvDepartureStation.text = train.departureStation
            tvArrivalStation.text = train.arrivalStation
            tvTrainNumber.text = train.number
            tvDuration.text = train.duration
            tvPrice.text = "¥${train.price.toInt()}起"
            llSeatInfo.removeAllViews()

            if (train.number.startsWith("中转")) {
                llSeatInfo.addView(seatColumn("中转车次", itemView.context.getColor(R.color.railway_red)))
            } else {
                val greenColor = itemView.context.getColor(R.color.green_soft)
                val candidateColor = itemView.context.getColor(R.color.railway_orange)
                val availability = seatInventoryRepository.getAvailabilities(train, departureDateProvider())
                val classes = if (train.routeType == RouteType.CONVENTIONAL) {
                    listOf("硬座" to "硬座", "硬卧" to "硬卧", "软卧" to "软卧", "无座" to "无座")
                } else if (train.isDongwo) {
                    listOf("二等" to "二等座", "软卧" to "软卧", "一等" to "一等座", "无座" to "无座")
                } else if (train.number.trim().startsWith("D", ignoreCase = true)) {
                    listOf("二等" to "二等座", "一等" to "一等座", "无座" to "无座")
                } else {
                    listOf("二等" to "二等座", "一等" to "一等座", "商务" to "商务座", "无座" to "无座")
                }
                classes.forEach { (label, seatType) ->
                    val stock = availability.getValue(seatType)
                    val text = android.text.SpannableString("$label  ${stock.displayLabel}").apply {
                        val statusStart = indexOf(stock.displayLabel).coerceAtLeast(0)
                        setSpan(
                            android.text.style.ForegroundColorSpan(
                                if (stock.requiresWaitlist) candidateColor else greenColor
                            ),
                            statusStart,
                            length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    llSeatInfo.addView(seatColumn(text, itemView.context.getColor(R.color.text_primary)))
                }
            }

            // 设置点击事件
            itemView.setOnClickListener {
                onTrainClick(train)
            }
        }

        private fun seatColumn(text: CharSequence, textColor: Int): TextView = TextView(itemView.context).apply {
            this.text = text
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            gravity = android.view.Gravity.CENTER
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }
}
