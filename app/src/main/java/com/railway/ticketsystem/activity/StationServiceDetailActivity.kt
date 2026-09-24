package com.railway.ticketsystem.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.StationServiceCatalog
import com.railway.ticketsystem.data.StationServiceRepository
import com.railway.ticketsystem.data.StationServiceSpec
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Station
import java.util.Locale

/** Full-screen, independent booking flow for one station service product. */
class StationServiceDetailActivity : ImmersiveActivity() {
    private lateinit var spec: StationServiceSpec
    private lateinit var repository: StationServiceRepository
    private lateinit var users: UserRepository
    private lateinit var stationView: TextView
    private lateinit var schedule: TextInputEditText
    private lateinit var contact: TextInputEditText
    private lateinit var detail: TextInputEditText
    private lateinit var result: TextView
    private var station = ""

    private val stationPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        val selected = response.data?.getSerializableExtra("selectedStation") as? Station
        if (response.resultCode == RESULT_OK && selected != null) {
            station = selected.name
            stationView.text = station
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spec = StationServiceCatalog.find(intent.getStringExtra(EXTRA_SERVICE_TYPE).orEmpty())
            ?: StationServiceCatalog.all().first()
        station = intent.getStringExtra(EXTRA_STATION).orEmpty()
        repository = StationServiceRepository(this)
        users = UserRepository(this)
        setContentView(screen())
    }

    private fun screen(): View = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@StationServiceDetailActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(34))
            addView(header())
            addView(label("车站服务 · 独立预约", 14, R.color.text_secondary), lp(bottom = 16))
            addView(overview())
            addView(label("服务内容", 18, R.color.text_primary, true), lp(top = 22))
            addView(highlights(), lp(top = 10))
            addView(label("预约信息", 18, R.color.text_primary, true), lp(top = 22))
            addView(form(), lp(top = 10))
        })
    }

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(back(), LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(label(spec.label, 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun overview() = card().apply {
        addView(LinearLayout(this@StationServiceDetailActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(18), dp(19), dp(18))
            addView(label(spec.label, 22, R.color.text_primary, true))
            addView(label(spec.subtitle, 14, R.color.text_secondary).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(top = 7))
            val fee = if (spec.priceCents > 0L) "服务费 ¥%.2f".format(Locale.CHINA, spec.priceCents / 100.0) else "预约不收取服务费"
            addView(label("当前可预约 · " + fee, 13, R.color.railway_blue, true), lp(top = 13))
        })
    }

    private fun highlights() = card().apply {
        addView(LinearLayout(this@StationServiceDetailActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(16))
            spec.highlights.forEachIndexed { index, item ->
                addView(label((index + 1).toString() + "  " + item, 14, R.color.text_primary))
                if (index != spec.highlights.lastIndex) addView(divider(), lp(top = 11, bottom = 11, height = 1))
            }
        })
    }

    private fun form() = card().apply {
        addView(LinearLayout(this@StationServiceDetailActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(18))
            addView(label("服务车站", 13, R.color.text_secondary))
            stationView = label(if (station.isBlank()) "请选择车站" else station, 17, R.color.text_primary, true).apply {
                setPadding(0, dp(11), 0, dp(11))
                isClickable = true; isFocusable = true
                contentDescription = "选择服务车站"
                setOnClickListener { stationPicker.launch(Intent(this@StationServiceDetailActivity, StationSelectionActivity::class.java)) }
            }
            addView(stationView, lp())
            addView(divider(), lp(height = 1))
            schedule = field(spec.scheduleHint, spec.scheduleHint)
            addView(schedule.parent as View, lp(top = 12))
            contact = field("联系人及联系方式", "姓名 · 手机号")
            contact.setText(users.getCurrentUser()?.let { listOf(it.realName.ifBlank { it.username }, it.phone).filter(String::isNotBlank).joinToString(" · ") }.orEmpty())
            addView(contact.parent as View, lp(top = 10))
            detail = field(spec.detailHint, spec.detailHint, true)
            addView(detail.parent as View, lp(top = 10))
            addView(label(spec.notice, 12, R.color.text_secondary).apply { setLineSpacing(dp(2).toFloat(), 1f) }, lp(top = 13))
            addView(submit(), lp(top = 16, height = 52))
            result = label("", 13, R.color.success)
            result.visibility = View.GONE
            addView(result, lp(top = 13))
        })
    }

    private fun field(title: String, hint: String, multi: Boolean = false): TextInputEditText {
        val wrap = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = title
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = ContextCompat.getColor(this@StationServiceDetailActivity, R.color.divider)
            defaultHintTextColor = ColorStateList.valueOf(ContextCompat.getColor(this@StationServiceDetailActivity, R.color.text_secondary))
            setBoxCornerRadii(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat())
        }
        return TextInputEditText(this).apply {
            this.hint = hint; textSize = 15f
            setTextColor(ContextCompat.getColor(this@StationServiceDetailActivity, R.color.text_primary))
            if (multi) { minLines = 3; maxLines = 5; gravity = Gravity.TOP or Gravity.START; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
            wrap.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun submit() = MaterialButton(this).apply {
        isAllCaps = false; textSize = 16f; cornerRadius = dp(26)
        text = if (spec.priceCents > 0L) "确认并支付 ¥%.2f".format(Locale.CHINA, spec.priceCents / 100.0) else "提交" + spec.label + "预约"
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@StationServiceDetailActivity, R.color.railway_blue))
        setTextColor(Color.WHITE)
        setOnClickListener { save() }
    }

    private fun save() {
        if (station.isBlank()) { Toast.makeText(this, "请先选择服务车站", Toast.LENGTH_SHORT).show(); return }
        val details = detail.text?.toString()?.trim().orEmpty()
        if (spec.requiresDetail && details.isBlank()) { Toast.makeText(this, "请补充" + spec.detailHint, Toast.LENGTH_SHORT).show(); return }
        val user = users.getCurrentUser() ?: run { Toast.makeText(this, "请先登录后提交车站服务", Toast.LENGTH_SHORT).show(); return }
        val time = schedule.text?.toString()?.trim().orEmpty()
        val request = StationServiceRepository.newRequest(user.id, spec.label, station, time, contact.text?.toString()?.trim().orEmpty().ifBlank { user.phone }, details, if (spec.priceCents > 0L) "待服务" else "已提交", spec.priceCents, intent.getStringExtra(EXTRA_TICKET_ORDER_ID))
        if (!repository.save(request)) { Toast.makeText(this, "服务提交失败，请稍后重试", Toast.LENGTH_SHORT).show(); return }
        MessageRepository(this).add(user.id, MessageRepository.TRAVEL, spec.label + "已提交", station + " · " + spec.confirmation(time, details), eventKey = "station_service:" + request.id)
        result.text = "预约已保存 · 服务编号 " + request.id + "\n可在“我的服务预约”中查看处理进度。"
        result.visibility = View.VISIBLE
        Toast.makeText(this, "预约已提交", Toast.LENGTH_SHORT).show()
    }

    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@StationServiceDetailActivity, R.color.divider); setCardBackgroundColor(Color.parseColor("#B8FFFFFF")) }
    private fun back() = MaterialButton(this).apply { text = "‹"; textSize = 31f; isAllCaps = false; insetTop = 0; insetBottom = 0; minWidth = 0; minHeight = 0; setPadding(0, 0, 0, dp(4)); backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT); setTextColor(ContextCompat.getColor(this@StationServiceDetailActivity, R.color.text_primary)); contentDescription = "返回"; setOnClickListener { finish() } }
    private fun divider() = View(this).apply { setBackgroundColor(ContextCompat.getColor(this@StationServiceDetailActivity, R.color.divider)) }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@StationServiceDetailActivity, color)); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun lp(top: Int = 0, bottom: Int = 0, height: Int = -2) = LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SERVICE_TYPE = "station_service_type"
        const val EXTRA_STATION = "station_service_station"
        const val EXTRA_TICKET_ORDER_ID = "station_service_ticket_order_id"
        fun intent(context: Context, service: String, station: String = "", ticket: String? = null) = Intent(context, StationServiceDetailActivity::class.java).putExtra(EXTRA_SERVICE_TYPE, service).putExtra(EXTRA_STATION, station).putExtra(EXTRA_TICKET_ORDER_ID, ticket)
    }
}



