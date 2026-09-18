package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.TravelExpenseLine
import com.railway.ticketsystem.data.TravelExpenseReport
import com.railway.ticketsystem.data.TravelExpenseRepository
import com.railway.ticketsystem.data.TravelExpenseSnapshot
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityTravelExpenseCenterBinding

/** One place for every local travel cost and the corresponding report-approval simulation. */
class TravelExpenseCenterActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityTravelExpenseCenterBinding
    private lateinit var users: UserRepository
    private lateinit var expenses: TravelExpenseRepository
    private var renderedSnapshot = TravelExpenseSnapshot(emptyList(), emptyMap())
    private var renderedReport: TravelExpenseReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTravelExpenseCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        users = UserRepository(this)
        expenses = TravelExpenseRepository(this)
        binding.btnExpenseBack.setOnClickListener { finish() }
        binding.btnExpenseReportAction.setOnClickListener { advanceReport() }
        binding.btnShareExpenseList.setOnClickListener { shareExpenseList() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val user = users.getCurrentUser()
        binding.llExpenseRows.removeAllViews()
        if (user == null) {
            binding.tvExpenseTotal.text = "¥0.00"
            binding.tvExpenseSummary.text = "登录后自动归集车票、酒店、餐饮与车站服务消费。"
            binding.tvExpenseHint.text = "请先登录后查看出行账本。"
            binding.btnExpenseReportAction.isEnabled = false
            binding.btnShareExpenseList.isEnabled = false
            return
        }
        renderedSnapshot = expenses.snapshot(user.id)
        val totals = renderedSnapshot.categoryTotals
        binding.tvExpenseTotal.text = "¥${TravelExpenseRepository.money(renderedSnapshot.totalCents)}"
        binding.tvExpenseSummary.text = if (renderedSnapshot.lines.isEmpty()) {
            "本月暂无可归集的出行消费"
        } else {
            "已自动归集 ${renderedSnapshot.lines.size} 笔消费 · ${TravelExpenseRepository.currentPeriod()}"
        }
        binding.tvTicketTotal.text = "车票 ¥${TravelExpenseRepository.money(totals["火车票"] ?: 0L)}"
        binding.tvHotelTotal.text = "酒店 ¥${TravelExpenseRepository.money(totals["酒店"] ?: 0L)}"
        binding.tvMealTotal.text = "餐饮 ¥${TravelExpenseRepository.money(totals["餐饮"] ?: 0L)}"
        binding.tvServiceTotal.text = "服务 ¥${TravelExpenseRepository.money(totals["车站服务"] ?: 0L)}"
        binding.tvExpenseHint.text = if (renderedSnapshot.lines.isEmpty()) {
            "购票、预订酒店、车上餐饮或有偿车站服务后，费用会自动出现在这里。"
        } else "费用明细按创建或支付时间排列，可用于生成企业报销单。"
        renderedSnapshot.lines.forEach { binding.llExpenseRows.addView(expenseRow(it)) }
        renderedReport = expenses.getByUser(user.id).firstOrNull { it.period == TravelExpenseRepository.currentPeriod() }
        renderReportState(renderedReport)
        binding.btnShareExpenseList.isEnabled = renderedSnapshot.lines.isNotEmpty()
    }

    private fun renderReportState(report: TravelExpenseReport?) {
        when (report?.status) {
            null -> {
                binding.tvExpenseReportStatus.text = "尚未创建 ${TravelExpenseRepository.currentPeriod()} 报销单"
                binding.btnExpenseReportAction.text = "生成本月报销单"
                binding.btnExpenseReportAction.isEnabled = renderedSnapshot.lines.isNotEmpty()
            }
            "草稿" -> {
                binding.tvExpenseReportStatus.text = "${report.id} · 草稿 · ¥${TravelExpenseRepository.money(report.totalCents)}"
                binding.btnExpenseReportAction.text = "提交企业审批"
                binding.btnExpenseReportAction.isEnabled = true
            }
            "审批中" -> {
                binding.tvExpenseReportStatus.text = "${report.id} · 审批中 · 已提交 ${report.submittedAt.orEmpty()}"
                binding.btnExpenseReportAction.text = "模拟审批通过"
                binding.btnExpenseReportAction.isEnabled = true
            }
            else -> {
                binding.tvExpenseReportStatus.text = "${report.id} · 已通过 · ${report.approvedAt.orEmpty()}"
                binding.btnExpenseReportAction.text = "本月报销单已通过"
                binding.btnExpenseReportAction.isEnabled = false
            }
        }
    }

    private fun advanceReport() {
        val user = users.getCurrentUser() ?: return
        val result = when (val report = renderedReport) {
            null -> expenses.createReport(user.id, renderedSnapshot)
            else -> when (report.status) {
                "草稿" -> expenses.submit(report)
                "审批中" -> expenses.approve(report)
                else -> report
            }
        }
        if (result == null) {
            Toast.makeText(this, "暂无可报销的出行消费", Toast.LENGTH_SHORT).show()
            return
        }
        val message = when (result.status) {
            "草稿" -> "已生成报销单，可核对后提交"
            "审批中" -> "已提交企业审批"
            "已通过" -> "企业审批已通过"
            else -> "报销单已更新"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun shareExpenseList() {
        if (renderedSnapshot.lines.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, expenses.exportText(renderedSnapshot, renderedReport))
        startActivity(Intent.createChooser(send, "发送费用清单"))
    }

    private fun expenseRow(line: TravelExpenseLine): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@TravelExpenseCenterActivity, R.color.divider)
            setCardBackgroundColor(Color.argb(184, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(line.category).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        header.addView(text("¥${TravelExpenseRepository.money(line.amountCents)}", 16, R.color.railway_blue_deep, true))
        content.addView(header)
        content.addView(text(line.title, 15, R.color.text_primary, true).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        })
        content.addView(text("${line.occurredAt} · 单号 ${line.id}", 12, R.color.text_secondary, false).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) }
        })
        card.addView(content)
        return card
    }

    private fun label(value: String) = text(value, 13, R.color.railway_blue, true).apply {
        background = ContextCompat.getDrawable(this@TravelExpenseCenterActivity, R.drawable.bg_passenger_count)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ContextCompat.getColor(this@TravelExpenseCenterActivity, color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
