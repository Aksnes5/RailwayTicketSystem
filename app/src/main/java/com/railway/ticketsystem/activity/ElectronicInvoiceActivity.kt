package com.railway.ticketsystem.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.ElectronicInvoice
import com.railway.ticketsystem.data.InvoicePdfExporter
import com.railway.ticketsystem.data.InvoiceRepository
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityElectronicInvoiceBinding
import com.railway.ticketsystem.model.Order
import java.util.Locale

class ElectronicInvoiceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityElectronicInvoiceBinding
    private lateinit var userRepository: UserRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var invoiceRepository: InvoiceRepository
    private lateinit var messageRepository: MessageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityElectronicInvoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userRepository = UserRepository(this)
        orderRepository = OrderRepository(this)
        invoiceRepository = InvoiceRepository(this)
        messageRepository = MessageRepository(this)
        binding.btnBack.setOnClickListener { finish() }
        userRepository.getCurrentUser()?.let { binding.etInvoiceTitle.setText(it.realName.ifBlank { it.username }) }
        render()
    }

    private fun render() {
        val user = userRepository.getCurrentUser()
        if (user == null) {
            binding.tvInvoiceHint.text = "请先登录后申请电子发票。"
            return
        }
        val eligible = orderRepository.getOrdersByUserId(user.id).filter { it.status == "已完成" }
        binding.llInvoiceOrders.removeAllViews()
        binding.tvInvoiceHint.text = if (eligible.isEmpty()) {
            "暂无可开具发票的已完成行程。行程到达后可在这里申请开票。"
        } else {
            "以下已完成行程可申请电子普通发票。"
        }
        eligible.sortedByDescending { it.departureDate }.forEach { order ->
            binding.llInvoiceOrders.addView(invoiceCard(order, invoiceRepository.getByTicket(user.id, order.id)))
        }
    }

    private fun invoiceCard(order: Order, invoice: ElectronicInvoice?): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@ElectronicInvoiceActivity, R.color.divider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(text("${order.trainNumber}  ${order.departureStation} → ${order.arrivalStation}", 16, R.color.text_primary, true))
        content.addView(text("${order.departureDate} · 铁路客运服务 · 价税合计 ${money((order.finalPrice * 100).toLong())}", 13, R.color.text_secondary, false).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        })
        if (invoice == null) {
            content.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "开具电子发票"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@ElectronicInvoiceActivity, R.color.railway_blue))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(12) }
                setOnClickListener { issue(order) }
            })
        } else {
            content.addView(text("已开具 · 发票代码 ${invoice.invoiceCode}\n发票号码 ${invoice.invoiceNumber} · 校验码 ${invoice.verificationCode}\n不含税 ${money(invoice.amountCents)}  税额 ${money(invoice.taxCents)}  税率 9%\n开具时间 ${invoice.issueDate}", 12, R.color.railway_blue, false).apply {
                setLineSpacing(dp(4).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
            })
            content.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = if (invoice.pdfUri.isNullOrBlank()) "生成并保存 PDF" else "查看 PDF"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@ElectronicInvoiceActivity, R.color.railway_blue))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(12) }
                setOnClickListener {
                    if (invoice.pdfUri.isNullOrBlank()) generatePdf(order, invoice, openAfter = true)
                    else openPdf(invoice.pdfUri.orEmpty())
                }
            })
            if (!invoice.pdfUri.isNullOrBlank()) {
                content.addView(text("PDF 已保存至：下载 / 铁路12306 / 电子发票", 12, R.color.text_secondary, false).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                })
            }
        }
        card.addView(content)
        return card
    }

    private fun issue(order: Order) {
        val user = userRepository.getCurrentUser() ?: return
        val invoice = invoiceRepository.issue(user.id, order, binding.etInvoiceTitle.text?.toString().orEmpty())
        if (invoice == null) {
            Toast.makeText(this, "发票开具失败，请确认行程已完成", Toast.LENGTH_SHORT).show()
            return
        }
        val savedInvoice = generatePdf(order, invoice, openAfter = false)
        if (savedInvoice == null) {
            Toast.makeText(this, "发票已开具，但 PDF 生成失败，请点击重试", Toast.LENGTH_LONG).show()
            render()
            return
        }
        messageRepository.add(
            user.id,
            MessageRepository.PAYMENT,
            "电子发票已开具 · ${order.trainNumber}",
            "发票号码${savedInvoice.invoiceNumber}，价税合计${money(savedInvoice.totalCents)}，PDF 已保存至下载目录。",
            order.id,
            eventKey = "invoice:${savedInvoice.id}"
        )
        Toast.makeText(this, "电子发票 PDF 已生成并保存", Toast.LENGTH_LONG).show()
        render()
        openPdf(savedInvoice.pdfUri.orEmpty())
    }

    private fun generatePdf(order: Order, invoice: ElectronicInvoice, openAfter: Boolean): ElectronicInvoice? {
        val user = userRepository.getCurrentUser() ?: return null
        val uri = InvoicePdfExporter.export(this, invoice, order)
        if (uri == null) {
            Toast.makeText(this, "PDF 生成失败，请稍后重试", Toast.LENGTH_SHORT).show()
            return null
        }
        val saved = invoiceRepository.attachPdf(user.id, invoice.id, uri.toString())
        if (saved == null) {
            Toast.makeText(this, "PDF 已生成，但发票记录保存失败", Toast.LENGTH_SHORT).show()
            return null
        }
        render()
        if (openAfter) openPdf(saved.pdfUri.orEmpty())
        return saved
    }

    private fun openPdf(uriText: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriText), "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            val message = if (it is ActivityNotFoundException) "PDF 已保存，请使用文件管理器打开" else "PDF 已保存至下载目录"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ContextCompat.getColor(this@ElectronicInvoiceActivity, color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun money(cents: Long) = "¥" + String.format(Locale.CHINA, "%.2f", cents / 100.0)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
