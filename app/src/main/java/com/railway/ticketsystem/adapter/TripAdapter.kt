package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.TravelAssistant
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.*

class TripAdapter(
    private val onTripClick: (Order) -> Unit
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    private var trips: List<Order> = emptyList()

    fun updateTrips(newTrips: List<Order>) {
        if (trips == newTrips) return
        val previous = trips
        val next = newTrips.toList()
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition].id == next[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition] == next[newItemPosition]
        })
        trips = next
        changes.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trip, parent, false)
        return TripViewHolder(view)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int = trips.size

    inner class TripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTrainNumber: TextView = itemView.findViewById(R.id.tvTripTrainNumber)
        private val tvRoute: TextView = itemView.findViewById(R.id.tvTripRoute)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTripTime)
        private val tvReminder: TextView = itemView.findViewById(R.id.tvTripReminder)
        private val tvPassenger: TextView = itemView.findViewById(R.id.tvTripPassenger)
        private val tvSeat: TextView = itemView.findViewById(R.id.tvTripSeat)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvTripPrice)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvTripStatus)

        fun bind(order: Order) {
            tvTrainNumber.text = order.trainNumber
            tvRoute.text = "${order.departureStation} → ${order.arrivalStation}"
            tvTime.text = "${order.departureDate} ${order.departureTime} - ${order.arrivalTime}"
            val reminder = TravelAssistant.boardingReminder(order)
            tvReminder.text = reminder
            tvReminder.visibility = if (reminder == null) View.GONE else View.VISIBLE
            val groupSize = order.groupPassengerCount.coerceAtLeast(1)
            tvPassenger.text = if (groupSize > 1) {
                "${order.passengerName} · 同行${groupSize}人"
            } else {
                order.passengerName
            }
            tvSeat.text = order.seatInfo
            tvPrice.text = "¥${order.finalPrice.toInt()}"
            tvStatus.text = order.status
            
            // 设置状态颜色
            when (order.status) {
                "已支付" -> tvStatus.setTextColor(itemView.context.getColor(R.color.green))
                "待支付" -> tvStatus.setTextColor(itemView.context.getColor(R.color.railway_red))
                "已完成" -> tvStatus.setTextColor(itemView.context.getColor(R.color.gray_medium))
                "已取消" -> tvStatus.setTextColor(itemView.context.getColor(R.color.gray_medium))
                else -> tvStatus.setTextColor(itemView.context.getColor(R.color.railway_blue))
            }
            
            // 设置点击事件
            itemView.setOnClickListener {
                onTripClick(order)
            }
        }
    }
}



