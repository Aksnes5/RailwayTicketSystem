package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MemberCoupon
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.MembershipTask
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityMembershipCenterSafeBinding
import java.util.Locale

class MembershipCenterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMembershipCenterSafeBinding
    private lateinit var users: UserRepository
    private lateinit var membership: MembershipRepository
    private var userId = ""
    private var repositoriesReady = false
    private var pageBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { bindMembershipPage() }.onFailure { error ->
            Log.e("MembershipCenter", "会员页面初始化失败", error)
            pageBound = false
            showStartupFallback()
        }
    }

    private fun bindMembershipPage() {
        binding = ActivityMembershipCenterSafeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "会员与积分中心"
        initializeRepositories()
        // The dedicated 去充值 button is hidden; tapping the balance block opens the same dialog.
        binding.llWalletBalance.setOnClickListener {
            if (repositoriesReady) showRechargeDialog() else showMembershipUnavailable()
        }
        binding.btnCheckIn.setOnClickListener {
            if (!repositoriesReady) {
                showMembershipUnavailable()
                return@setOnClickListener
            }
            if (membership.checkIn(userId)) {
                Toast.makeText(this, "签到成功，积分已到账", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "今日已签到或任务尚不可领取", Toast.LENGTH_SHORT).show()
            }
            renderSafely()
        }
        pageBound = true
    }

    private fun showStartupFallback() {
        val message = TextView(this).apply {
            text = "会员中心暂无法加载\n\n本地账户与订单数据未被删除。请返回后重试。"
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(message) })
    }

    override fun onResume() {
        super.onResume()
        if (!pageBound) return
        if (!repositoriesReady && !initializeRepositories()) {
            showMembershipUnavailable()
            return
        }
        userId = runCatching { users.getCurrentUser()?.id.orEmpty() }.getOrDefault("")
        renderSafely()
    }

    private fun initializeRepositories(): Boolean {
        repositoriesReady = runCatching {
            users = UserRepository(this)
            membership = MembershipRepository(this)
            userId = users.getCurrentUser()?.id.orEmpty()
        }.onFailure { Log.e("MembershipCenter", "会员存储不可用", it) }.isSuccess
        return repositoriesReady
    }

    private fun renderSafely() {
        if (!repositoriesReady) {
            showMembershipUnavailable()
            return
        }
        runCatching { render() }.onFailure { error ->
            Log.e("MembershipCenter", "会员数据加载失败", error)
            val user = runCatching { users.getCurrentUser() }.getOrNull()
            if (user == null) {
                showMembershipUnavailable()
                return@onFailure
            }
            binding.tvMemberName.text = user.username + " 的铁路会员"
            binding.tvMemberLevel.text = memberLevel(user.points)
            binding.tvMemberRights.text = "会员服务正在同步，请稍后刷新重试。"
            binding.tvGrowthSummary.text = "成长足迹正在同步，请稍后刷新。"
            binding.tvMemberPoints.text = user.points.toString()
            binding.tvWalletBalance.text = "¥0.00"
            binding.btnCheckIn.isEnabled = false
            binding.llTaskList.removeAllViews()
            binding.llCouponOffers.removeAllViews()
            binding.tvCouponSummary.text = "权益包正在同步，请稍后刷新。"
            binding.tvPointLedger.text = "积分流水正在同步，请稍后刷新。"
            binding.tvWalletLedger.text = "钱包流水正在同步，请稍后刷新。"
        }
    }

    private fun showMembershipUnavailable() {
        binding.tvMemberName.text = "铁路会员"
        binding.tvMemberLevel.text = "会员服务暂不可用"
        binding.tvMemberRights.text = "本地数据正在保护中，请稍后返回重试。"
        binding.tvGrowthSummary.text = "成长足迹暂不可用"
        binding.tvMemberPoints.text = "—"
        binding.tvWalletBalance.text = "—"
        binding.btnCheckIn.isEnabled = false
        binding.llTaskList.removeAllViews()
        binding.llCouponOffers.removeAllViews()
        binding.tvCouponSummary.text = "权益包将在数据可用后恢复显示。"
        binding.tvPointLedger.text = "暂无法读取积分流水。"
        binding.tvWalletLedger.text = "暂无法读取钱包流水。"
    }

    private fun render() {
        val user = users.getCurrentUser()
        if (user == null || userId.isBlank()) {
            showMembershipUnavailable()
            return
        }
        binding.tvMemberName.text = user.username + " 的铁路会员"
        binding.tvMemberLevel.text = memberLevel(user.points)
        binding.tvMemberRights.text = memberRights(user.points)
        binding.tvGrowthSummary.text = membership.getGrowthProfile(userId).summary
        binding.tvMemberPoints.text = user.points.toString()
        binding.tvWalletBalance.text = membership.getWalletBalanceText(userId)
        val tasks = membership.getTasks(userId)
        val checked = tasks.firstOrNull { it.id == "daily_check_in" }
        binding.btnCheckIn.isEnabled = checked?.claimed != true
        binding.btnCheckIn.text = if (checked?.claimed == true) "今日已签到" else "今日签到领取 10 积分"
        renderTasks(tasks)
        renderOffers()
        renderCoupons(membership.getCoupons(userId))
        renderLedgers()
    }

    private fun renderTasks(tasks: List<MembershipTask>) {
        binding.llTaskList.removeAllViews()
        tasks.forEach { task ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
                setPadding(0, dp(8), 0, dp(8))
            }
            // Title and reward share one weighted group, so "+N" sits immediately after the
            // closing parenthesis instead of being pushed to the far right of the row.
            val summary = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val body = TextView(this).apply {
                text = task.title + "（" + task.progress + "/" + task.target + "）"
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTextColor(getColor(R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val reward = TextView(this).apply {
                text = "+" + task.rewardPoints
                textSize = 13f
                maxLines = 1
                includeFontPadding = false
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.railway_blue))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(4) }
            }
            summary.addView(body)
            summary.addView(reward)
            val action = MaterialButton(this).apply {
                textSize = 12f
                minWidth = 0
                minHeight = 0
                minimumHeight = dp(38)
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(19)
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(dp(76), dp(38))
                when {
                    task.claimed -> {
                        text = "已领取"
                        isEnabled = true
                        isClickable = false
                        backgroundTintList = ColorStateList.valueOf(getColor(R.color.success_container))
                        setTextColor(getColor(R.color.success))
                        strokeColor = ColorStateList.valueOf(getColor(R.color.success))
                        strokeWidth = dp(1)
                    }
                    task.completed -> {
                        text = "领取"
                        isEnabled = true
                        backgroundTintList = ColorStateList.valueOf(getColor(R.color.railway_blue))
                        setTextColor(getColor(R.color.white))
                        strokeWidth = 0
                        setOnClickListener {
                            if (membership.claimTask(userId, task.id)) {
                                Toast.makeText(this@MembershipCenterActivity, "任务奖励已到账", Toast.LENGTH_SHORT).show()
                                renderSafely()
                            }
                        }
                    }
                    else -> {
                        text = "进行中"
                        isEnabled = true
                        isClickable = false
                        backgroundTintList = ColorStateList.valueOf(getColor(R.color.railway_blue_light))
                        setTextColor(getColor(R.color.railway_blue))
                        strokeColor = ColorStateList.valueOf(getColor(R.color.railway_blue))
                        strokeWidth = dp(1)
                    }
                }
            }
            row.addView(summary)
            row.addView(action)
            binding.llTaskList.addView(row)
        }
    }

    private fun renderOffers() {
        binding.llCouponOffers.removeAllViews()
        listOf(
            Triple(MembershipRepository.COUPON_CASH, "¥20 购票抵扣券", 220),
            Triple(MembershipRepository.COUPON_UPGRADE, "升座体验券", 450),
            Triple(MembershipRepository.COUPON_CHANGE, "免费改签券", 320),
            Triple(MembershipRepository.COUPON_LOUNGE, "高铁贵宾候车厅券", 520)
        ).forEach { offer ->
            val button = MaterialButton(this).apply {
                text = offer.second + "  ·  " + offer.third + " 积分兑换"
                isAllCaps = false
                textSize = 13f
                setOnClickListener { confirmRedeem(offer.first, offer.second, offer.third) }
            }
            binding.llCouponOffers.addView(button)
        }
    }

    private fun confirmRedeem(type: String, title: String, cost: Int) {
        AlertDialog.Builder(this)
            .setTitle("兑换 " + title)
            .setMessage("将扣除 " + cost + " 积分。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认兑换") { _, _ ->
                if (membership.redeemCoupon(userId, type) != null) {
                    Toast.makeText(this, "兑换成功，已放入权益包", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "积分不足或兑换失败", Toast.LENGTH_SHORT).show()
                }
                renderSafely()
            }.show()
    }

    private fun renderCoupons(coupons: List<MemberCoupon>) {
        binding.tvCouponSummary.text = if (coupons.isEmpty()) {
            "权益包暂无券，完成任务或使用积分兑换后会显示在这里。"
        } else {
            "我的权益包（" + coupons.size + "）\n" + coupons.take(6).joinToString("\n") {
                "• " + it.title + " · " + it.status + " · " + it.createdAt
            }
        }
    }

    private fun renderLedgers() {
        val points = membership.getPointLedger(userId).take(8)
        binding.tvPointLedger.text = if (points.isEmpty()) "暂无积分流水，完成购票、候补兑现或任务即可获得积分。" else
            "积分流水\n" + points.joinToString("\n") {
                it.createdAt + "  " + cleanLegacyLabel(it.title) + "  " + String.format(Locale.CHINA, "%+d", it.delta)
            }
        val wallet = membership.getWalletLedger(userId).take(6)
        binding.tvWalletLedger.text = if (wallet.isEmpty()) "暂无钱包流水，充值后可用余额支付车票。" else
            "钱包流水\n" + wallet.joinToString("\n") {
                it.createdAt + "  " + cleanLegacyLabel(it.title) + "  " +
                    String.format(Locale.CHINA, "%+.2f", it.amountCents / 100.0)
            }
    }

    private fun showRechargeDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择充值金额")
            .setMessage("充值后可直接用于订单支付")
            .setView(content)
            .setNegativeButton("取消", null)
            .create()
        listOf(100, 200, 500, 1000).forEach { amount ->
            content.addView(MaterialButton(this).apply {
                text = "充值 ¥" + amount
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    if (membership.rechargeWallet(userId, amount * 100L)) {
                        Toast.makeText(this@MembershipCenterActivity, "已充值 ¥" + amount + "，可在订单支付页使用", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        renderSafely()
                    }
                }
            })
        }
        dialog.show()
    }

    private fun cleanLegacyLabel(value: String): String =
        value.replace("\u6a21\u62df", "").replace("本地电子凭证", "电子凭证")

    private fun memberLevel(points: Int): String = when {
        points >= 3_000 -> "铂金会员 · 专属出行权益"
        points >= 1_000 -> "金卡会员 · 积分加速成长"
        points >= 300 -> "银卡会员 · 解锁更多权益"
        else -> "铁路会员 · 从每日任务开始成长"
    }

    private fun memberRights(points: Int): String = when {
        points >= 3_000 -> "铂金权益：专属客服标识 · 积分兑换优先 · 出行任务奖励展示"
        points >= 1_000 -> "金卡权益：兑换权益包 · 钱包快捷支付 · 丰富成长任务"
        points >= 300 -> "银卡权益：每日任务加速 · 优先解锁升座与改签权益"
        else -> "会员权益：每日签到、任务积分、权益兑换和铁路钱包。累计 300 分升级银卡。"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
