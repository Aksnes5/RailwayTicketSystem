package com.railway.ticketsystem.activity

import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.OfflinePackStatus
import com.railway.ticketsystem.data.OfflineTravelRepository
import com.railway.ticketsystem.data.UserRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Controls the durable pre-trip snapshot and the optional national offline map atlas. */
class OfflineTravelPackActivity : ImmersiveActivity() {
    private lateinit var repository: OfflineTravelRepository
    private lateinit var userRepository: UserRepository
    private lateinit var tvStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var switchAuto: SwitchMaterial
    private lateinit var btnPrepare: MaterialButton
    private lateinit var btnMap: MaterialButton
    private lateinit var btnSatellite: MaterialButton
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OfflineTravelRepository(this)
        userRepository = UserRepository(this)
        setContentView(buildScreen())
        refresh()
    }

    private fun buildScreen() = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@OfflineTravelPackActivity).also { root ->
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(dp(18), dp(18), dp(18), dp(28))
            val header = LinearLayout(this@OfflineTravelPackActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(MaterialButton(this@OfflineTravelPackActivity).apply {
                text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(52), dp(48)))
            header.addView(text("离线出行包", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(header)
            root.addView(text("车票、经停时刻、站内线路地图、酒店订单和电子凭证将保存在本机。网络恢复后，重新下载即可同步最新状态。", 14, R.color.text_secondary, false), margin(top = 8, bottom = 16))
            root.addView(autoCard())
            root.addView(actionCard(), margin(top = 12))
            tvProgress = text("", 14, R.color.railway_blue_deep, false).apply { visibility = View.GONE }
            root.addView(tvProgress, margin(top = 12))
            tvStatus = text("", 14, R.color.text_secondary, false).apply { setLineSpacing(dp(4).toFloat(), 1f) }
            root.addView(tvStatus, margin(top = 12))
            root.addView(quietButton("清理本机离线内容") {
                repository.clear(); refresh(); Toast.makeText(this@OfflineTravelPackActivity, "已清理离线缓存", Toast.LENGTH_SHORT).show()
            }, margin(top = 18))
        })
    }

    private fun autoCard() = card().apply {
        addView(LinearLayout(this@OfflineTravelPackActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("自动出行包", 17, R.color.text_primary, true))
            switchAuto = SwitchMaterial(this@OfflineTravelPackActivity).apply {
                text = "支付成功后自动保存下一段行程"; isChecked = repository.isAutoDownloadEnabled()
                setOnCheckedChangeListener { _, checked -> repository.setAutoDownloadEnabled(checked) }
            }
            addView(switchAuto, margin(top = 8))
            addView(text("自动保存不会下载大体积地图；全国高清地图由您在下方确认下载。", 13, R.color.text_secondary, false), margin(top = 3))
        })
    }

    private fun actionCard() = card().apply {
        addView(LinearLayout(this@OfflineTravelPackActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("离线内容", 17, R.color.text_primary, true))
            addView(text("行程包会保留已支付/已完成车票的座席、路线和经停时刻，以及已保存的酒店、电子凭证记录。", 13, R.color.text_secondary, false), margin(top = 6))
            btnPrepare = button("下载 / 更新本次行程") { prepare(includeMap = false) }
            addView(btnPrepare, margin(top = 14))
            btnMap = quietButton("下载全国铁路地图") { prepare(includeMap = true) }
            addView(btnMap, margin(top = 8))
            btnSatellite = quietButton("下载高清卫星影像离线包") { prepare(includeSatellite = true) }
            addView(btnSatellite, margin(top = 8))
            addView(text("卫星包下载全国概览，并额外缓存已保存行程站点周边 15–16 级高清影像；避免一次下载全国最高层级造成超大存储占用。", 13, R.color.railway_blue_deep, false), margin(top = 10))
        })
    }

    private fun prepare(includeMap: Boolean = false, includeSatellite: Boolean = false) {
        val user = userRepository.getCurrentUser()
        if (user == null) { Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show(); return }
        btnPrepare.isEnabled = false; btnMap.isEnabled = false; btnSatellite.isEnabled = false; tvProgress.visibility = View.VISIBLE
        executor.execute {
            val result = runCatching {
                repository.prepareForUpcomingTrips(user.id, includeMap, includeSatellite) { message -> runOnUiThread { tvProgress.text = message } }
            }
            runOnUiThread {
                btnPrepare.isEnabled = true; btnMap.isEnabled = true; btnSatellite.isEnabled = true; tvProgress.visibility = View.GONE
                result.onSuccess { status -> refresh(status); Toast.makeText(this, if (includeSatellite) "高清卫星影像已下载" else if (includeMap) "全国离线地图已下载" else "行程离线包已更新", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "离线包下载失败，请检查网络后重试", Toast.LENGTH_LONG).show(); refresh() }
            }
        }
    }

    private fun refresh(status: OfflinePackStatus = repository.status()) {
        if (!::tvStatus.isInitialized) return
        val time = if (status.updatedAt > 0) SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(status.updatedAt)) else "尚未下载"
        val map = if (status.hasNationalMap) "已下载（${formatBytes(status.mapBytes)}）" else "未下载"
        tvStatus.text = "最近更新：$time\n车票与经停：${status.tripCount} 段 · 酒店订单：${status.hotelCount} 条 · 电子凭证：${status.invoiceCount} 份\n全国高清地图：$map"
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@OfflineTravelPackActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@OfflineTravelPackActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@OfflineTravelPackActivity, color)); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun button(value: String, click: () -> Unit) = MaterialButton(this).apply { text = value; textSize = 16f; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 14f; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun formatBytes(bytes: Long) = when { bytes >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024f / 1024f); bytes >= 1024 -> "${bytes / 1024} KB"; else -> "$bytes B" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}