package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.databinding.ItemMessageBinding
import com.railway.ticketsystem.model.AppMessage

class MessageAdapter(private val onClick: (AppMessage) -> Unit) : RecyclerView.Adapter<MessageAdapter.Holder>() {
    private var items: List<AppMessage> = emptyList()

    fun submit(messages: List<AppMessage>) {
        items = messages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: AppMessage) {
            binding.tvMessageTag.text = when (message.category) {
                "payment" -> "支付"
                "waitlist" -> "候补"
                "travel" -> "行程"
                else -> "车票"
            }
            binding.tvMessageTitle.text = userFacing(message.title)
            binding.tvMessageContent.text = userFacing(message.content)
            binding.tvMessageTime.text = message.createdAt
            val color = if (message.isRead) R.color.gray_medium else R.color.railway_blue
            binding.tvMessageTag.setTextColor(ContextCompat.getColor(binding.root.context, color))
            binding.root.alpha = if (message.isRead) 0.72f else 1f
            binding.root.setOnClickListener { onClick(message) }
        }
    }

    private fun userFacing(value: String): String = value
        .replace("\u6a21\u62df", "")
        .replace("\u4ec5\u7528\u4e8e\u672c\u5730\u6f14\u793a\uff0c\u4e0d\u4f1a\u4ea7\u751f\u771f\u5b9e\u6263\u6b3e\u3002", "")
        .replace("本地电子凭证", "电子凭证")
        .replace("支付已完成，", "支付已完成。")
}
