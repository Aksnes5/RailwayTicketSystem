package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.Train

class TicketAdapter(
    private val onBookTicketClick: (Train) -> Unit
) : RecyclerView.Adapter<TicketAdapter.TicketViewHolder>() {

    private var tickets = mutableListOf<Train>()

    fun updateTickets(newTickets: List<Train>) {
        tickets.clear()
        tickets.addAll(newTickets)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        holder.bind(tickets[position])
    }

    override fun getItemCount(): Int = tickets.size

    inner class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTrainNumber: TextView = itemView.findViewById(R.id.tvTrainNumber)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvDepartureStation: TextView = itemView.findViewById(R.id.tvDepartureStation)
        private val tvDepartureTime: TextView = itemView.findViewById(R.id.tvDepartureTime)
        private val tvArrivalStation: TextView = itemView.findViewById(R.id.tvArrivalStation)
        private val tvArrivalTime: TextView = itemView.findViewById(R.id.tvArrivalTime)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvAvailableSeats: TextView = itemView.findViewById(R.id.tvAvailableSeats)
        private val btnBookTicket: com.google.android.material.button.MaterialButton = 
            itemView.findViewById(R.id.btnBookTicket)

        fun bind(train: Train) {
            tvTrainNumber.text = train.number
            tvPrice.text = "¥${train.price.toInt()}"
            tvDepartureStation.text = train.departureStation
            tvDepartureTime.text = train.departureTime
            tvArrivalStation.text = train.arrivalStation
            tvArrivalTime.text = train.arrivalTime
            tvDuration.text = train.duration
            tvAvailableSeats.text = if (train.availableSeats > 20) "有票" else "余票${train.availableSeats}张"

            btnBookTicket.setOnClickListener {
                onBookTicketClick(train)
            }
        }
    }
}
