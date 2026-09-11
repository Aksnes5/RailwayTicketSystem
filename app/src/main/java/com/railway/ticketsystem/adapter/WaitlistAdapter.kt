package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.WaitlistRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaitlistAdapter(
    private val onCancel: (WaitlistRequest) -> Unit
) : RecyclerView.Adapter<WaitlistAdapter.WaitlistViewHolder>() {
    private val requests = mutableListOf<WaitlistRequest>()

    fun updateRequests(items: List<WaitlistRequest>) {
        requests.clear()
        requests.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaitlistViewHolder =
        WaitlistViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_waitlist, parent, false))

    override fun onBindViewHolder(holder: WaitlistViewHolder, position: Int) = holder.bind(requests[position])

    override fun getItemCount(): Int = requests.size

    inner class WaitlistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val train: TextView = itemView.findViewById(R.id.tvWaitlistTrain)
        private val route: TextView = itemView.findViewById(R.id.tvWaitlistRoute)
        private val time: TextView = itemView.findViewById(R.id.tvWaitlistTime)
        private val seat: TextView = itemView.findViewById(R.id.tvWaitlistSeat)
        private val status: TextView = itemView.findViewById(R.id.tvWaitlistStatus)
        private val cancel: MaterialButton = itemView.findViewById(R.id.btnCancelWaitlist)
        private val progress: TextView = itemView.findViewById(R.id.tvWaitlistProgress)

        fun bind(request: WaitlistRequest) {
            val order = request.requestedOrder
            train.text = order.trainNumber
            route.text = request.route
            time.text = "${order.departureDate} ${order.departureTime} 发车"
            seat.text = "候补 ${order.seatType} · ${order.passengerName}"
            status.text = request.status
            status.setTextColor(itemView.context.getColor(when (request.status) {
                "候补中" -> R.color.railway_blue
                "已兑现" -> R.color.green
                "兑现失败" -> R.color.railway_red
                else -> R.color.gray_medium
            }))
            progress.text = when (request.status) {
                "候补中" -> "兑现概率 ${request.successProbability}% · ${evaluationDescription(request.evaluationTime)}"
                else -> request.resultMessage ?: request.status
            }
            cancel.visibility = if (request.status == "候补中") View.VISIBLE else View.GONE
            cancel.setOnClickListener { onCancel(request) }
        }

        private fun evaluationDescription(time: Long): String = if (time <= 0L) {
            "即将结算"
        } else {
            "预计 ${SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(time))} 出结果"
        }
    }
}
