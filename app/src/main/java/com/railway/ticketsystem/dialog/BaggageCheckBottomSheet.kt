package com.railway.ticketsystem.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.BaggageRegulationCatalog
import com.railway.ticketsystem.data.BaggageRegulationCatalog.BaggageItem
import com.railway.ticketsystem.data.BaggageRegulationCatalog.ComplianceLevel
import java.util.Locale

/**
 * 铁路随身行李安检合规与充电宝Wh计算弹窗
 */
class BaggageCheckBottomSheet(private val context: Context) {

    private val dialog = BottomSheetDialog(context)
    private var selectedCategory: String = "全部"
    private var searchQuery: String = ""

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_baggage_check, null)
        dialog.setContentView(view)

        val etSearch = view.findViewById<EditText>(R.id.etBaggageSearch)
        val etPowerBankMah = view.findViewById<EditText>(R.id.etPowerBankMah)
        val tvWhResult = view.findViewById<TextView>(R.id.tvPowerBankWhResult)
        val tvPowerStatus = view.findViewById<TextView>(R.id.tvPowerBankComplianceStatus)
        val llCategories = view.findViewById<LinearLayout>(R.id.llBaggageCategoryPills)
        val llItemsContainer = view.findViewById<LinearLayout>(R.id.llBaggageItemsContainer)
        val btnClose = view.findViewById<View>(R.id.btnCloseBaggageCheck)

        btnClose.setOnClickListener { dialog.dismiss() }

        // 1. 充电宝实时计算
        fun updatePowerBankCalculation() {
            val mah = etPowerBankMah.text.toString().trim().toDoubleOrNull() ?: 0.0
            val wh = BaggageRegulationCatalog.calculatePowerBankWh(mah, 3.7)
            tvWhResult.text = String.format(Locale.CHINA, "%.1f Wh", wh)

            val evaluation = BaggageRegulationCatalog.evaluatePowerBank(wh)
            when (evaluation.level) {
                ComplianceLevel.SAFE -> {
                    tvPowerStatus.text = "🟢 ${evaluation.title} · ${evaluation.message}"
                    tvPowerStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                }
                ComplianceLevel.RESTRICTED -> {
                    tvPowerStatus.text = "🟡 ${evaluation.title} · ${evaluation.message}"
                    tvPowerStatus.setTextColor(ContextCompat.getColor(context, R.color.orange))
                }
                ComplianceLevel.PROHIBITED -> {
                    tvPowerStatus.text = "🔴 ${evaluation.title} · ${evaluation.message}"
                    tvPowerStatus.setTextColor(ContextCompat.getColor(context, R.color.railway_red))
                }
            }
        }

        etPowerBankMah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePowerBankCalculation()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        updatePowerBankCalculation()

        // 2. 刷新物品列表
        fun renderItems() {
            llItemsContainer.removeAllViews()
            val filtered = BaggageRegulationCatalog.items.filter { item ->
                val matchCat = (selectedCategory == "全部" || item.category == selectedCategory)
                val matchQuery = searchQuery.isBlank() ||
                        item.name.contains(searchQuery, ignoreCase = true) ||
                        item.limitDescription.contains(searchQuery, ignoreCase = true) ||
                        item.category.contains(searchQuery, ignoreCase = true)
                matchCat && matchQuery
            }

            if (filtered.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = "未找到相关物品携带规定\n如需携带特殊器械，请在进站安检处主动向工作人员申报咨询"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, dp(24))
                }
                llItemsContainer.addView(emptyTv)
                return
            }

            for (item in filtered) {
                val itemCard = buildItemCard(item)
                llItemsContainer.addView(itemCard)
            }
        }

        // 3. 搜索监听
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                renderItems()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. 分类标签
        val categories = listOf("全部", "数码电池", "喷雾日化", "酒水食品", "刀具工具", "出行运动")
        for (cat in categories) {
            val pill = TextView(context).apply {
                text = cat
                textSize = 12f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                val isSelected = (cat == selectedCategory)
                background = if (isSelected) {
                    ContextCompat.getDrawable(context, R.drawable.bg_quick_date_pill_active)
                } else {
                    ContextCompat.getDrawable(context, R.drawable.bg_quick_date_pill)
                }
                setTextColor(if (isSelected) ContextCompat.getColor(context, R.color.railway_blue) else ContextCompat.getColor(context, R.color.text_secondary))
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener {
                    selectedCategory = cat
                    // 更新所有标签样式
                    for (i in 0 until llCategories.childCount) {
                        val child = llCategories.getChildAt(i) as? TextView ?: continue
                        val sel = (child.text == selectedCategory)
                        child.background = if (sel) {
                            ContextCompat.getDrawable(context, R.drawable.bg_quick_date_pill_active)
                        } else {
                            ContextCompat.getDrawable(context, R.drawable.bg_quick_date_pill)
                        }
                        child.setTextColor(if (sel) ContextCompat.getColor(context, R.color.railway_blue) else ContextCompat.getColor(context, R.color.text_secondary))
                        child.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                    }
                    renderItems()
                }
            }
            llCategories.addView(pill)
        }

        renderItems()
        dialog.show()
    }

    private fun buildItemCard(item: BaggageItem): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F7FAFE"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#E0ECF8"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }

            // 标题行
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(context).apply {
                text = item.name
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(titleTv)

            val badgeTv = TextView(context).apply {
                text = item.level.label
                textSize = 11f
                setPadding(dp(8), dp(2), dp(8), dp(2))
                val badgeColor = Color.parseColor(item.level.badgeColorHex)
                setTextColor(badgeColor)
                background = GradientDrawable().apply {
                    setColor(Color.argb(25, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor)))
                    cornerRadius = dp(8).toFloat()
                }
            }
            headerRow.addView(badgeTv)
            addView(headerRow)

            // 限量规则说明
            val descTv = TextView(context).apply {
                text = "标准：${item.limitDescription}"
                textSize = 12.5f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, dp(6), 0, dp(2))
            }
            addView(descTv)

            // 官方依据
            val ruleTv = TextView(context).apply {
                text = "法规依据：${item.officialRule}"
                textSize = 11.5f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, dp(2), 0, dp(4))
            }
            addView(ruleTv)

            // 携带贴士
            val tipTv = TextView(context).apply {
                text = "💡 携带建议：${item.suggestion}"
                textSize = 11.5f
                setTextColor(Color.parseColor("#0066CC"))
            }
            addView(tipTv)
        }
    }

    private fun dp(dpVal: Int): Int {
        return (dpVal * context.resources.displayMetrics.density).toInt()
    }
}
