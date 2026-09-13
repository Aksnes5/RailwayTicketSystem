package com.railway.ticketsystem.fragment

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MemberCoupon
import com.railway.ticketsystem.data.MemberTravelSnapshot
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.MembershipTask
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityMembershipCenterSafeBinding
import java.util.Locale

/**
 * The membership home is a first-class bottom-navigation destination. Keeping it
 * in the main activity avoids an activity launch animation and retains tab state.
 */
class MembershipCenterFragment : Fragment() {
    private lateinit var binding: ActivityMembershipCenterSafeBinding
    private lateinit var users: UserRepository
    private lateinit var membership: MembershipRepository
    private var userId = ""
    private var repositoriesReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivityMembershipCenterSafeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                Toast.makeText(requireContext(), "签到成功，积分已到账", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "今日已签到或任务尚不可领取", Toast.LENGTH_SHORT).show()
            }
            renderSafely()
        }
        refreshMembership()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded) refreshMembership()
    }

    private fun refreshMembership() {
        if (!repositoriesReady && !initializeRepositories()) {
            showMembershipUnavailable()
            return
        }
        userId = runCatching { users.getCurrentUser()?.id.orEmpty() }.getOrDefault("")
        renderSafely()
    }

    private fun initializeRepositories(): Boolean {
        repositoriesReady = runCatching {
            val context = requireContext()
            users = UserRepository(context)
            membership = MembershipRepository(context)
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
            binding.tvMemberName.text = "积分与权益账户"
            binding.tvMemberLevel.text = memberLevel(user.points)
            binding.tvMemberRights.text = "会员服务正在同步，请稍后刷新重试。"
            binding.tvGrowthSummary.text = "成长足迹正在同步，请稍后刷新。"
            renderTravelGrowthUnavailable("出行数据正在同步，请稍后刷新。")
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
        binding.tvMemberName.text = "积分与权益账户"
        binding.tvMemberLevel.text = "会员服务暂不可用"
        binding.tvMemberRights.text = "本地数据正在保护中，请稍后返回重试。"
        binding.tvGrowthSummary.text = "成长足迹暂不可用"
        renderTravelGrowthUnavailable("出行数据暂不可用")
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
        binding.tvMemberName.text = "积分与权益账户"
        binding.tvMemberLevel.text = memberLevel(user.points)
        binding.tvMemberRights.text = memberRights(user.points)
        binding.tvGrowthSummary.text = membership.getGrowthProfile(userId).summary
        renderTravelGrowth(membership.getTravelGrowthSnapshot(userId, user.points))
        binding.tvMemberPoints.text = user.points.toString()
        binding.tvWalletBalance.text = membership.getWalletBalanceText(userId)
        val tasks = membership.getTasks(userId)
        val checked = tasks.firstOrNull { it.id == "daily_check_in" }
        val checkedIn = checked?.claimed == true
        binding.btnCheckIn.apply {
            text = if (checkedIn) "今日已签到" else "今日签到领取 10 积分"
            isEnabled = true
            isClickable = !checkedIn
            if (checkedIn) {
                setBackgroundResource(R.drawable.bg_liquid_success)
                backgroundTintList = null
                setTextColor(requireContext().getColor(R.color.success))
            } else {
                setBackgroundResource(R.drawable.bg_liquid_action)
                backgroundTintList = null
                setTextColor(requireContext().getColor(R.color.railway_blue_deep))
            }
        }
        renderTasks(tasks)
        renderOffers()
        renderCoupons(membership.getCoupons(userId))
        renderLedgers()
    }

    private fun renderTravelGrowth(snapshot: MemberTravelSnapshot) {
        binding.tvYearMileage.text = String.format(Locale.CHINA, "%,d km", snapshot.mileageKm)
        binding.tvTravelCities.text = "${snapshot.cities.size} 城"
        binding.tvCarbonReduction.text = "${snapshot.carbonReductionKg} kg"
        binding.tvCityFootprint.text = if (snapshot.cities.isEmpty()) {
            "出行城市地图\n完成首段旅程后，在这里点亮你的第一座城市"
        } else {
            "出行城市地图 · 已点亮 ${snapshot.cities.size} 城\n" + snapshot.cities.joinToString(" · ")
        }
        binding.tvFrequentRoutes.text = if (snapshot.frequentRoutes.isEmpty()) {
            "常坐线路\n暂无出行记录，购票出发后自动形成偏好线路"
        } else {
            "常坐线路\n" + snapshot.frequentRoutes.joinToString("\n")
        }
        binding.tvGrowthStreak.text = "连续签到 ${snapshot.checkInStreak} 天 · 第 7 天可额外领取 20 积分"
        val unlocked = snapshot.badges.filter { it.unlocked }
        binding.tvGrowthBadges.text = buildString {
            append("徽章成就 · 已获得 ${unlocked.size}/${snapshot.badges.size}\n")
            append(snapshot.badges.joinToString("\n") { badge ->
                if (badge.unlocked) "● ${badge.title} · ${badge.description}" else "○ ${badge.title} · ${badge.description}"
            })
        }
        binding.tvMonthlyChallenge.text = "月度挑战 · ${snapshot.monthlyTrips}/${snapshot.monthlyTarget}\n本月再完成 ${maxOf(0, snapshot.monthlyTarget - snapshot.monthlyTrips)} 段出行，即可领取 60 积分"
        binding.tvTravelLeaderboard.text = "出行成长排行榜 · 第 ${snapshot.leaderboardRank} 位\n超过 ${snapshot.leaderboardPercentile}% 的同级会员"
    }

    private fun renderTravelGrowthUnavailable(message: String) {
        binding.tvYearMileage.text = "—"
        binding.tvTravelCities.text = "—"
        binding.tvCarbonReduction.text = "—"
        binding.tvCityFootprint.text = message
        binding.tvFrequentRoutes.text = message
        binding.tvGrowthStreak.text = message
        binding.tvGrowthBadges.text = message
        binding.tvMonthlyChallenge.text = message
        binding.tvTravelLeaderboard.text = message
    }

    private fun renderTasks(tasks: List<MembershipTask>) {
        binding.llTaskList.removeAllViews()
        tasks.forEach { task ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
                setBackgroundResource(R.drawable.bg_glass_control)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            // Title and reward share one weighted group, so "+N" sits immediately after the
            // closing parenthesis instead of being pushed to the far right of the row.
            val summary = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val body = TextView(requireContext()).apply {
                text = task.title + "（" + task.progress + "/" + task.target + "）"
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTextColor(requireContext().getColor(R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val reward = TextView(requireContext()).apply {
                text = "+" + task.rewardPoints
                textSize = 13f
                maxLines = 1
                includeFontPadding = false
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(requireContext().getColor(R.color.railway_blue))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(4) }
            }
            summary.addView(body)
            summary.addView(reward)
            val action = MaterialButton(requireContext()).apply {
                textSize = 12f
                minWidth = 0
                minHeight = 0
                minimumHeight = dp(38)
                isAllCaps = false
                cornerRadius = dp(19)
                layoutParams = LinearLayout.LayoutParams(
                    dp(76),
                    dp(38)
                )
                when {
                    task.claimed -> {
                        text = "已领取"
                        isEnabled = true
                        isClickable = false
                        backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.success_container))
                        setTextColor(requireContext().getColor(R.color.success))
                        strokeColor = ColorStateList.valueOf(requireContext().getColor(R.color.success))
                        strokeWidth = dp(1)
                    }
                    task.completed -> {
                        text = "领取"
                        isEnabled = true
                        backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.railway_blue))
                        setTextColor(requireContext().getColor(R.color.white))
                        strokeColor = ColorStateList.valueOf(requireContext().getColor(R.color.railway_blue))
                        strokeWidth = 0
                        setOnClickListener {
                            if (membership.claimTask(userId, task.id)) {
                                Toast.makeText(requireContext(), "任务奖励已到账", Toast.LENGTH_SHORT).show()
                                renderSafely()
                            }
                        }
                    }
                    else -> {
                        text = "进行中"
                        isEnabled = true
                        isClickable = false
                        backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.surface_container))
                        setTextColor(requireContext().getColor(R.color.text_secondary))
                        strokeColor = ColorStateList.valueOf(requireContext().getColor(R.color.button_stroke))
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
            val button = MaterialButton(requireContext()).apply {
                text = offer.second + "  ·  " + offer.third + " 积分兑换"
                isAllCaps = false
                textSize = 13f
                insetTop = 0
                insetBottom = 0
                setBackgroundResource(R.drawable.bg_glass_control)
                backgroundTintList = null
                setTextColor(requireContext().getColor(R.color.railway_blue_deep))
                setOnClickListener { confirmRedeem(offer.first, offer.second, offer.third) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46)
                ).apply { topMargin = dp(8) }
            }
            binding.llCouponOffers.addView(button)
        }
    }

    private fun confirmRedeem(type: String, title: String, cost: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("兑换 " + title)
            .setMessage("将扣除 " + cost + " 积分。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认兑换") { _, _ ->
                if (membership.redeemCoupon(userId, type) != null) {
                    Toast.makeText(requireContext(), "兑换成功，已放入权益包", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "积分不足或兑换失败", Toast.LENGTH_SHORT).show()
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
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("选择充值金额")
            .setMessage("充值后可直接用于订单支付")
            .setView(content)
            .setNegativeButton("取消", null)
            .create()
        listOf(100, 200, 500, 1000).forEach { amount ->
            content.addView(MaterialButton(requireContext()).apply {
                text = "充值 ¥" + amount
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    if (membership.rechargeWallet(userId, amount * 100L)) {
                        Toast.makeText(requireContext(), "已充值 ¥" + amount + "，可在订单支付页使用", Toast.LENGTH_SHORT).show()
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
}
