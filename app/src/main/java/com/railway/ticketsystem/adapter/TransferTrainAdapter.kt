package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.data.TrainSetResolver
import com.railway.ticketsystem.model.TransferRisk
import com.railway.ticketsystem.model.TransferTrain

/**
 * 中转车次适配器
 * 专门用于显示中转车次的详细信息
 */
class TransferTrainAdapter(
    private val seatInventoryRepository: SeatInventoryRepository,
    private val departureDateProvider: () -> String,
    private val onItemClick: (TransferTrain) -> Unit
) : RecyclerView.Adapter<TransferTrainAdapter.TransferTrainViewHolder>() {

    private var transferTrains = mutableListOf<TransferTrain>()

    fun updateTransferTrains(newTransferTrains: List<TransferTrain>) {
        transferTrains.clear()
        transferTrains.addAll(newTransferTrains)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferTrainViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transfer_train, parent, false)
        return TransferTrainViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransferTrainViewHolder, position: Int) {
        holder.bind(transferTrains[position])
    }

    override fun getItemCount(): Int = transferTrains.size

    inner class TransferTrainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoute: TextView = itemView.findViewById(R.id.tvRoute)
        private val tvDepartureTime: TextView = itemView.findViewById(R.id.tvDepartureTime)
        private val tvArrivalTime: TextView = itemView.findViewById(R.id.tvArrivalTime)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvDepartureStation: TextView = itemView.findViewById(R.id.tvDepartureStation)
        private val tvArrivalStation: TextView = itemView.findViewById(R.id.tvArrivalStation)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvTransferInfo: TextView = itemView.findViewById(R.id.tvTransferInfo)
        private val tvFirstLeg: TextView = itemView.findViewById(R.id.tvFirstLeg)
        private val tvSecondLeg: TextView = itemView.findViewById(R.id.tvSecondLeg)

        private val tvTransferRisk: TextView = itemView.findViewById(R.id.tvTransferRisk)
        private val tvTransferInventory: TextView = itemView.findViewById(R.id.tvTransferInventory)
        fun bind(transferTrain: TransferTrain) {
            tvRoute.text = "中转换乘"
            tvDepartureTime.text = transferTrain.departureTime
            tvArrivalTime.text = transferTrain.arrivalTime
            tvDepartureStation.text = transferTrain.departureStation
            tvArrivalStation.text = transferTrain.arrivalStation
            tvDuration.text = transferTrain.totalDuration
            tvPrice.text = "¥${transferTrain.totalPrice.toInt()}"
            tvTransferInfo.text = "${transferTrain.transferStation}换乘 · ${transferTrain.transferTime}分钟"
            
            // 显示两段车次的详细信息
            val departureDate = departureDateProvider()
            val firstStock = seatInventoryRepository.getAvailability(transferTrain.firstLeg, departureDate, "二等座")
            val secondStock = seatInventoryRepository.getAvailability(transferTrain.secondLeg, departureDate, "二等座")
            val canBookSecondClass = !firstStock.requiresWaitlist && !secondStock.requiresWaitlist
            val inventoryText = "二等座 · 第一程 ${firstStock.displayLabel}｜第二程 ${secondStock.displayLabel}"
            tvTransferInventory.text = inventoryText
            tvTransferInventory.setTextColor(itemView.context.getColor(
                if (canBookSecondClass) R.color.green else R.color.railway_orange
            ))
            tvTransferRisk.text = if (canBookSecondClass) transferTrain.riskLabel else "候补提醒"
            val riskColor = if (!canBookSecondClass) {
                R.color.railway_orange
            } else when (transferTrain.risk) {
                TransferRisk.STEADY -> R.color.green
                TransferRisk.TIGHT -> R.color.railway_orange
                TransferRisk.NOT_RECOMMENDED -> R.color.railway_red
            }
            tvTransferRisk.setTextColor(itemView.context.getColor(riskColor))
            tvFirstLeg.text = legText("第一程", transferTrain.firstLeg)
            tvSecondLeg.text = legText("第二程", transferTrain.secondLeg)

            itemView.setOnClickListener {
                onItemClick(transferTrain)
            }
        }

        private fun legText(index: String, train: com.railway.ticketsystem.model.Train): String {
            val trainSet = TrainSetResolver.modelFor(train)
            val runningInfo = buildString {
                append("${train.departureStation} → ${train.arrivalStation} · ${train.departureTime} - ${train.arrivalTime}")
                append(" · ${train.duration}")
                trainSet?.let { append(" · $it") }
            }
            return "$index  ${train.number}\n$runningInfo"
        }

    }

}
