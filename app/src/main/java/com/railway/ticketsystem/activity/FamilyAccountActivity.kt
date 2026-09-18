package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.FamilyAccountRepository
import com.railway.ticketsystem.data.FamilyMember
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Passenger

/** Household and frequent-companion manager. Every entry becomes a real selectable passenger. */
class FamilyAccountActivity : ImmersiveActivity() {
    private lateinit var content: LinearLayout
    private lateinit var familyRepository: FamilyAccountRepository
    private lateinit var passengerRepository: PassengerRepository
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        familyRepository = FamilyAccountRepository(this)
        passengerRepository = PassengerRepository(this)
        userRepository = UserRepository(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        renderMembers()
    }

    private fun buildScreen(): ScrollView {
        return ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_page_backdrop)
            addView(LinearLayout(this@FamilyAccountActivity).also { root ->
                root.orientation = LinearLayout.VERTICAL
                root.setPadding(dp(18), dp(18), dp(18), dp(28))
                val header = LinearLayout(this@FamilyAccountActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                header.addView(MaterialButton(this@FamilyAccountActivity).apply {
                    text = "‹"
                    textSize = 30f
                    minWidth = dp(48)
                    insetTop = 0; insetBottom = 0
                    contentDescription = "返回"
                    setOnClickListener { finish() }
                }, LinearLayout.LayoutParams(dp(52), dp(48)))
                header.addView(text("多人同行与家庭账户", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                root.addView(header)
                root.addView(text("建立家庭成员和常用同行人，儿童、学生、老人权益会在购票时清晰提示；多人出票自动优先同排与相邻座。", 14, R.color.text_secondary, false).apply {
                    setLineSpacing(dp(4).toFloat(), 1f)
                }, margin(top = 8, bottom = 16))
                root.addView(infoCard())
                val addFamily = button("添加家庭成员") { showMemberDialog(false) }
                val addCompanion = quietButton("添加常用同行人") { showMemberDialog(true) }
                root.addView(addFamily, margin(top = 14))
                root.addView(addCompanion, margin(top = 8, bottom = 18))
                content = LinearLayout(this@FamilyAccountActivity).apply { orientation = LinearLayout.VERTICAL }
                root.addView(content)
            })
        }
    }

    private fun infoCard() = card().apply {
        addView(LinearLayout(this@FamilyAccountActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("同行智能分配", 17, R.color.railway_blue_deep, true))
            addView(text("有足够余票时：优先同排；超过一排容量时：安排相邻两排；余票少于同行人数时：整单不出票。", 14, R.color.text_secondary, false).apply {
                setLineSpacing(dp(4).toFloat(), 1f)
            }, margin(top = 7))
        })
    }

    private fun renderMembers() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val user = userRepository.getCurrentUser()
        if (user == null) {
            content.addView(text("请登录后管理家庭账户。", 15, R.color.text_secondary, false))
            return
        }
        val members = familyRepository.members(user.id)
        content.addView(text("家庭成员与同行人 · ${members.size} 位", 17, R.color.text_primary, true), margin(bottom = 8))
        if (members.isEmpty()) {
            content.addView(card().apply {
                addView(text("暂未添加成员。添加后会同步到乘车人管理，购票时可多选并自动安排座位。", 14, R.color.text_secondary, false).apply {
                    setPadding(dp(18), dp(16), dp(18), dp(16))
                })
            })
            return
        }
        members.forEach { member -> content.addView(memberCard(member), margin(top = 10)) }
    }

    private fun memberCard(member: FamilyMember) = card().apply {
        addView(LinearLayout(this@FamilyAccountActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            addView(LinearLayout(this@FamilyAccountActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(member.name, 18, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(if (member.isFrequentCompanion) "常用同行人" else member.relation, 12, R.color.railway_blue_deep, true))
            })
            addView(text("${member.travelerType} · 身份证尾号 ${member.idCard.takeLast(4)}", 14, R.color.text_secondary, false), margin(top = 5))
            addView(text(member.benefitLabel, 13, R.color.success, false), margin(top = 5))
            addView(LinearLayout(this@FamilyAccountActivity).apply {
                gravity = Gravity.END
                addView(quietButton("移出家庭账户") {
                    familyRepository.remove(member.userId, member.id)
                    renderMembers()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { topMargin = dp(8) })
            })
        })
    }

    private fun showMemberDialog(frequent: Boolean) {
        val user = userRepository.getCurrentUser() ?: return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val name = field("姓名")
        val idCard = field("身份证号码（18 位）")
        val phone = field("手机号（11 位）")
        val relation = Spinner(this).apply { adapter = ArrayAdapter(this@FamilyAccountActivity, android.R.layout.simple_spinner_dropdown_item, listOf("配偶", "子女", "父母", "亲属", "朋友")) }
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@FamilyAccountActivity, android.R.layout.simple_spinner_dropdown_item, listOf("成人", "儿童", "学生", "老人")) }
        val frequentSwitch = SwitchMaterial(this).apply { text = "设为常用同行人"; isChecked = frequent }
        body.addView(name); body.addView(idCard, margin(top = 8)); body.addView(phone, margin(top = 8))
        body.addView(label("关系"), margin(top = 12)); body.addView(relation)
        body.addView(label("乘客类型"), margin(top = 10)); body.addView(type)
        body.addView(frequentSwitch, margin(top = 12))
        AlertDialog.Builder(this)
            .setTitle(if (frequent) "添加常用同行人" else "添加家庭成员")
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val cleanName = name.text.toString().trim(); val cleanId = idCard.text.toString().trim(); val cleanPhone = phone.text.toString().trim()
                        if (cleanName.isBlank() || cleanId.length != 18 || cleanPhone.length != 11) {
                            Toast.makeText(this, "请填写姓名、18 位身份证号和 11 位手机号", Toast.LENGTH_LONG).show(); return@setOnClickListener
                        }
                        val passengers = passengerRepository.getPassengersByUserId(user.id)
                        val passenger = passengers.firstOrNull { it.idCard == cleanId } ?: Passenger(
                            id = "PASSENGER_${System.currentTimeMillis()}", userId = user.id, name = cleanName,
                            idCard = cleanId, phone = cleanPhone, addTime = System.currentTimeMillis().toString()
                        ).also(passengerRepository::addPassenger)
                        val member = FamilyMember(
                            id = "FAMILY_${System.currentTimeMillis()}", userId = user.id, passengerId = passenger.id,
                            name = cleanName, relation = if (frequentSwitch.isChecked) "同行人" else relation.selectedItem.toString(),
                            travelerType = type.selectedItem.toString(), idCard = cleanId, phone = cleanPhone,
                            isFrequentCompanion = frequentSwitch.isChecked
                        )
                        if (familyRepository.upsert(member)) {
                            Toast.makeText(this, "已添加，可在购票时选择", Toast.LENGTH_SHORT).show(); dialog.dismiss(); renderMembers()
                        } else Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
        strokeColor = ContextCompat.getColor(this@FamilyAccountActivity, R.color.divider)
        setCardBackgroundColor(ContextCompat.getColor(this@FamilyAccountActivity, R.color.surface_container))
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@FamilyAccountActivity, color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun label(value: String) = text(value, 13, R.color.text_secondary, true)
    private fun field(hint: String) = EditText(this).apply { this.hint = hint; textSize = 15f; setSingleLine(true); backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@FamilyAccountActivity, R.color.railway_blue_light)) }
    private fun button(value: String, click: () -> Unit) = MaterialButton(this).apply { text = value; textSize = 16f; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 14f; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}