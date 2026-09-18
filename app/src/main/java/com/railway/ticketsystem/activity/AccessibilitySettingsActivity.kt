package com.railway.ticketsystem.activity

import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.AccessibilityPreferences

class AccessibilitySettingsActivity : ImmersiveActivity() {
    private lateinit var tvScale: TextView
    private var loading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        loading = false
    }

    private fun buildScreen() = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@AccessibilitySettingsActivity).also { root ->
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(dp(18), dp(18), dp(18), dp(28))
            val header = LinearLayout(this@AccessibilitySettingsActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(MaterialButton(this@AccessibilitySettingsActivity).apply { text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48)))
            header.addView(text("可访问性与性能", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(header)
            root.addView(text("为大字号、深色外观和读屏使用优化，同时减少列表动画与地图重复加载，提升操作流畅度。", 14, R.color.text_secondary, false), margin(top = 8, bottom = 16))
            root.addView(appearanceCard())
            root.addView(performanceCard(), margin(top = 12))
            root.addView(card().apply { addView(text("读屏支持已开启：所有新加的图标按钮提供明确标签；可点击文字和列表项目会自动补充可读名称。", 14, R.color.text_secondary, false).apply { setPadding(dp(18), dp(16), dp(18), dp(16)); setLineSpacing(dp(4).toFloat(), 1f) }) }, margin(top = 12))
        })
    }

    private fun appearanceCard() = card().apply {
        addView(LinearLayout(this@AccessibilitySettingsActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("显示与字号", 17, R.color.text_primary, true))
            val dark = SwitchMaterial(this@AccessibilitySettingsActivity).apply {
                text = "深色模式"; isChecked = AccessibilityPreferences.isDark(this@AccessibilitySettingsActivity)
                setOnCheckedChangeListener { _, checked -> if (!loading) { AccessibilityPreferences.setDark(this@AccessibilitySettingsActivity, checked); Toast.makeText(this@AccessibilitySettingsActivity, "已切换深色外观", Toast.LENGTH_SHORT).show() } }
            }
            addView(dark, margin(top = 8))
            tvScale = text("当前字号：${scaleText(AccessibilityPreferences.fontScale(this@AccessibilitySettingsActivity))}", 14, R.color.railway_blue_deep, true)
            addView(tvScale, margin(top = 12))
            val scale = AccessibilityPreferences.fontScale(this@AccessibilitySettingsActivity)
            addView(SeekBar(this@AccessibilitySettingsActivity).apply {
                max = 45; progress = ((scale - 0.9f) * 100).toInt().coerceIn(0, 45)
                contentDescription = "字号大小"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { tvScale.text = "当前字号：${scaleText(0.9f + progress / 100f)}" }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        AccessibilityPreferences.setFontScale(this@AccessibilitySettingsActivity, 0.9f + seekBar.progress / 100f)
                        Toast.makeText(this@AccessibilitySettingsActivity, "字号已应用到后续页面", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                })
            }, margin(top = 2))
            addView(text("可在 90%–135% 间调整；标题、表单、订单和列表都会遵循系统字号。", 13, R.color.text_secondary, false), margin(top = 4))
        })
    }

    private fun performanceCard() = card().apply {
        addView(LinearLayout(this@AccessibilitySettingsActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("流畅模式", 17, R.color.text_primary, true))
            addView(SwitchMaterial(this@AccessibilitySettingsActivity).apply {
                text = "降低列表和地图加载卡顿"; isChecked = AccessibilityPreferences.performanceMode(this@AccessibilitySettingsActivity)
                setOnCheckedChangeListener { _, checked -> AccessibilityPreferences.setPerformanceMode(this@AccessibilitySettingsActivity, checked) }
            }, margin(top = 8))
            addView(text("开启后，列表复用固定尺寸并关闭非必要的项目动画；地图优先使用本地缓存和离线资源。", 13, R.color.text_secondary, false), margin(top = 4))
        })
    }

    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@AccessibilitySettingsActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@AccessibilitySettingsActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@AccessibilitySettingsActivity, color)); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun scaleText(scale: Float) = "${(scale * 100).toInt()}%"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}