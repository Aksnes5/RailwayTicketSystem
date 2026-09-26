package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.Passenger

class PassengerAdapter(
    private val onPassengerClick: (Passenger) -> Unit,
    private val onEditClick: (Passenger) -> Unit,
    private val onDeleteClick: (Passenger) -> Unit
) : RecyclerView.Adapter<PassengerAdapter.PassengerViewHolder>() {

    private var passengers = mutableListOf<Passenger>()

    fun updatePassengers(newPassengers: List<Passenger>) {
        passengers.clear()
        passengers.addAll(newPassengers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PassengerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_passenger, parent, false)
        return PassengerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PassengerViewHolder, position: Int) {
        holder.bind(passengers[position])
    }

    override fun getItemCount(): Int = passengers.size

    inner class PassengerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPassengerAvatar: ImageView = itemView.findViewById(R.id.ivPassengerAvatar)
        private val tvPassengerName: TextView = itemView.findViewById(R.id.tvPassengerName)
        private val tvPassengerIdCard: TextView = itemView.findViewById(R.id.tvPassengerIdCard)
        private val tvPassengerPhone: TextView = itemView.findViewById(R.id.tvPassengerPhone)
        private val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)

        fun bind(passenger: Passenger) {
            tvPassengerName.text = "${passenger.name} (${passenger.ticketType})"
            tvPassengerIdCard.text = "身份证：${passenger.idCard}"
            tvPassengerPhone.text = "手机：${maskPhone(passenger.phone)}"

            // 设置点击事件
            itemView.setOnClickListener {
                onPassengerClick(passenger)
            }

            btnEdit.setOnClickListener {
                onEditClick(passenger)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(passenger)
            }
        }

        private fun maskPhone(phone: String): String {
            return if (phone.length >= 11) {
                "${phone.substring(0, 3)}****${phone.substring(7)}"
            } else {
                phone
            }
        }
    }
}