package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.FamilyAccountRepository
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.UserRepository
import java.util.Locale

/** A shareable detailed view for the membership travel-growth dashboard. */
class MemberGrowthActivity : ImmersiveActivity() {
    private val users by lazy { UserRepository(this) }
    private val membership by lazy { MembershipRepository(this) }
    private val family by lazy { FamilyAccountRepository(this) }
    private lateinit var body: LinearLayout
    private var reportText = ""

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(screen()) }
    override fun onResume() { super.onResume(); render() }
    private fun screen() = ScrollView(this).apply { setBackgroundResource(R.drawable.bg_page_backdrop); addView(LinearLayout(this@MemberGrowthActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(30)); addView(header()); addView(text("年度旅程、会员成就与家庭同行足迹", 14, R.color.text_secondary, false), margin(bottom = 16)); body = LinearLayout(this@MemberGrowthActivity).apply { orientation = LinearLayout.VERTICAL }; addView(body) }) }
    private fun render() {
        if (!::body.isInitialized) return; body.removeAllViews(); val user = users.getCurrentUser()
        if (user == null) { body.addView(card("暂无法读取成长数据", "登录后可汇总年度旅程与会员成长记录。")); return }
        val snapshot = membership.getTravelGrowthSnapshot(user.id, user.points); val members = family.members(user.id)
        reportText = "${snapshot.year} 年铁路出行报告\n年度里程 ${snapshot.mileageKm} km · 点亮 ${snapshot.cities.size} 城 · 减少碳排放 ${snapshot.carbonReductionKg} kg\n常坐线路：${snapshot.frequentRoutes.joinToString("；").ifBlank { "尚未形成" }}\n会员成长榜：第 ${snapshot.leaderboardRank} 位，超过 ${snapshot.leaderboardPercentile}% 同级会员"
        body.addView(card("${snapshot.year} 年出行报告", "${String.format(Locale.CHINA, "%,d", snapshot.mileageKm)} km 年度里程 · ${snapshot.tripCount} 段旅程 · ${snapshot.cities.size} 座城市\n累计减少 ${snapshot.carbonReductionKg} kg 碳排放"))
        body.addView(card("城市与常坐线路", "已点亮：${snapshot.cities.joinToString("、").ifBlank { "完成首段旅程后点亮城市" }}\n${snapshot.frequentRoutes.joinToString("\n").ifBlank { "暂无常坐线路" }}"), margin(top = 12))
        body.addView(card("徽章成就", snapshot.badges.joinToString("\n") { if (it.unlocked) "● ${it.title} · ${it.description}" else "○ ${it.title} · ${it.description}" }), margin(top = 12))
        body.addView(card("月度挑战与排行榜", "本月已完成 ${snapshot.monthlyTrips}/${snapshot.monthlyTarget} 段出行 · 连续签到 ${snapshot.checkInStreak} 天\n成长榜第 ${snapshot.leaderboardRank} 位，超过 ${snapshot.leaderboardPercentile}% 同级会员"), margin(top = 12))
        body.addView(card("家庭同行", if (members.isEmpty()) "暂未添加家庭成员；建立家庭账户后，可为儿童、学生和长者保留同行权益。" else "已关联 ${members.size} 位家人\n" + members.joinToString("\n") { "${it.name} · ${it.benefitLabel}" }), margin(top = 12))
        body.addView(quietButton("分享年度出行报告") { shareReport() }, margin(top = 18))
    }
    private fun shareReport() { if (reportText.isBlank()) { Toast.makeText(this, "报告正在生成", Toast.LENGTH_SHORT).show(); return }; startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reportText), "分享出行报告")) }
    private fun header() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; addView(MaterialButton(this@MemberGrowthActivity).apply { text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48))); addView(text("出行数据与成长", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f)) }
    private fun card(title: String, detail: String) = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@MemberGrowthActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@MemberGrowthActivity, R.color.surface_container)); addView(LinearLayout(this@MemberGrowthActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); addView(text(title, 17, R.color.text_primary, true)); addView(text(detail, 14, R.color.text_secondary, false).apply { setLineSpacing(dp(3).toFloat(), 1f) }, margin(top = 6)) }) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@MemberGrowthActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 15f; isAllCaps = false; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
