package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.TravelRecoveryOption
import com.railway.ticketsystem.data.TravelRecoveryPlanner
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.util.IosBottomSheetHelper
import com.railway.ticketsystem.util.LiquidGlassUi
import com.railway.ticketsystem.util.applyIosPressScale

/**
 * A single entry point for the useful things a passenger needs when a plan changes.
 * It deliberately does not manufacture a delay: its state comes from the stored order and clock.
 */
class TravelRecoveryAssistantActivity : ImmersiveActivity() {
    private lateinit var orderRepository: OrderRepository
    private lateinit var messageRepository: MessageRepository
    private var order: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderRepository = OrderRepository(this)
        messageRepository = MessageRepository(this)
        val reqOrderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: intent.getStringExtra("orderId")
        val reqTrain = intent.getStringExtra("trainNumber")
        order = if (!reqOrderId.isNullOrBlank()) {
            orderRepository.getOrderById(reqOrderId)
        } else if (!reqTrain.isNullOrBlank()) {
            orderRepository.getAllOrders().firstOrNull { it.trainNumber.equals(reqTrain, ignoreCase = true) }
                ?: orderRepository.getAllOrders().firstOrNull { it.status == "已支付" }
                ?: orderRepository.getAllOrders().firstOrNull()
        } else {
            orderRepository.getAllOrders().firstOrNull { it.status == "已支付" }
                ?: orderRepository.getAllOrders().firstOrNull()
        }
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4FAFF"))
            clipToPadding = false
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(32))
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(dp(20), bars.top + dp(12), dp(20), bars.bottom + dp(24))
            insets
        }

        content.addView(header())
        val active = order
        if (active == null) {
            content.addView(messageCard("行程暂不可用", "未能读取该车票。请返回行程列表后重新进入。"))
            return scroll
        }
        val plan = TravelRecoveryPlanner.create(active)
        content.addView(summaryCard(active, plan.status, plan.headline, plan.detail))
        content.addView(sectionTitle("可立即处理"))
        plan.options.forEach { content.addView(optionCard(active, it)) }
        content.addView(messageCard(
            "服务说明",
            "保障建议根据已保存的车票、时刻与订单状态生成；车站现场广播、公告和工作人员指引具有优先效力。"
        ))
        return scroll
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@TravelRecoveryAssistantActivity).apply {
            text = "‹"
            textSize = 42f
            setTextColor(Color.parseColor("#1B6FCB"))
            gravity = Gravity.CENTER
            contentDescription = "返回"
            setOnClickListener { finish() }
            applyIosPressScale()
        }, LinearLayout.LayoutParams(dp(46), dp(48)))
        addView(TextView(this@TravelRecoveryAssistantActivity).apply {
            text = "异常出行助手"
            textSize = 23f
            setTextColor(Color.parseColor("#102A43"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(LiquidGlassUi.capsule(this@TravelRecoveryAssistantActivity, "行程保障"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        }
    }

    private fun summaryCard(order: Order, status: String, headline: String, detail: String): View =
        LiquidGlassUi.card(this, emphasized = true).apply {
            layoutParams = cardParams(bottom = 18)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(context).apply {
                        text = "${order.trainNumber} · ${order.departureStation} → ${order.arrivalStation}"
                        textSize = 15f
                        setTextColor(Color.parseColor("#174B7A"))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(LiquidGlassUi.capsule(context, status))
                })
                addView(TextView(context).apply {
                    text = headline
                    textSize = 20f
                    setTextColor(Color.parseColor("#102A43"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(14), 0, dp(7))
                })
                addView(TextView(context).apply {
                    text = detail
                    textSize = 14f
                    setTextColor(Color.parseColor("#54708B"))
                    setLineSpacing(dp(3).toFloat(), 1f)
                })
            })
        }

    private fun optionCard(order: Order, option: TravelRecoveryOption): View =
        LiquidGlassUi.card(this).apply {
            layoutParams = cardParams(bottom = 12)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                addView(TextView(context).apply {
                    text = option.title
                    textSize = 17f
                    setTextColor(Color.parseColor("#102A43"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = option.description
                    textSize = 13.5f
                    setTextColor(Color.parseColor("#5C7790"))
                    setLineSpacing(dp(3).toFloat(), 1f)
                    setPadding(0, dp(6), 0, dp(13))
                })
                addView(LiquidGlassUi.actionButton(context, option.actionLabel, primary = option.type == TravelRecoveryOption.Type.MONITOR).apply {
                    setOnClickListener { executeAction(order, option) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            })
        }

    private fun messageCard(title: String, body: String): View =
        LiquidGlassUi.listRow(this, title, body).apply {
            layoutParams = cardParams(bottom = 12)
            isClickable = false
            isFocusable = false
        }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Color.parseColor("#183F63"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        }
    }

    private fun executeAction(order: Order, option: TravelRecoveryOption) {
        when (option.type) {
            TravelRecoveryOption.Type.MONITOR -> {
                messageRepository.add(
                    userId = order.userId,
                    category = MessageRepository.TRAVEL,
                    title = "行程保障已开启",
                    content = "${order.trainNumber} ${order.departureStation}→${order.arrivalStation} 已开启出发与经停关注。",
                    relatedOrderId = order.id,
                    eventKey = "travel_recovery_monitor:${order.id}"
                )
                Toast.makeText(this, "已开启行程关注", Toast.LENGTH_SHORT).show()
            }
            TravelRecoveryOption.Type.CONNECTION -> {
                IosBottomSheetHelper.showModal(
                    context = this,
                    title = "🔀 换乘与接驳协同 · ${order.arrivalStation}",
                    subtitle = "保障方案：接驳、站内换乘与行李搬运",
                    badgeText = "换乘保障"
                ) { dialog, container ->
                    container.addView(IosBottomSheetHelper.createPrimaryButton(this, "🚗 空铁/公铁多式联运与接驳打车") {
                        dialog.dismiss()
                        startActivity(Intent(this, RailIntermodalTransitActivity::class.java).apply {
                            putExtra(RailIntermodalTransitActivity.EXTRA_LINKED_TRAIN, order.trainNumber)
                            putExtra(RailIntermodalTransitActivity.EXTRA_DESTINATION_STATION, order.arrivalStation)
                        })
                    })
                    container.addView(IosBottomSheetHelper.createSecondaryButton(this, "🗺️ 站内微导航 · 便捷换乘通道") {
                        dialog.dismiss()
                        startActivity(Intent(this, IndoorNavigationActivity::class.java).apply {
                            putExtra(IndoorNavigationActivity.EXTRA_STATION, order.arrivalStation)
                            putExtra(IndoorNavigationActivity.EXTRA_TICKET_ORDER_ID, order.id)
                        })
                    })
                    container.addView(IosBottomSheetHelper.createSecondaryButton(this, "🧳 车站爱心搬运与行李寄存服务") {
                        dialog.dismiss()
                        startActivity(Intent(this, StationServiceActivity::class.java).apply {
                            putExtra(StationServiceActivity.EXTRA_STATION, order.arrivalStation)
                            putExtra(StationServiceActivity.EXTRA_TICKET_ORDER_ID, order.id)
                        })
                    })
                }
            }
            TravelRecoveryOption.Type.HOTEL -> startActivity(Intent(this, HotelBookingActivity::class.java).apply {
                putExtra(HotelBookingActivity.EXTRA_STATION, order.arrivalStation)
            })
            TravelRecoveryOption.Type.MEAL -> startActivity(Intent(this, MealOrderActivity::class.java).putExtra("orderId", order.id))
            TravelRecoveryOption.Type.CHANGE -> {
                IosBottomSheetHelper.showModal(
                    context = this,
                    title = "⚡ 晚点改签与备选车次",
                    subtitle = "${order.departureStation} → ${order.arrivalStation} 智能改签与同线车次",
                    badgeText = "改签方案"
                ) { dialog, container ->
                    container.addView(IosBottomSheetHelper.createPrimaryButton(this, "🔄 立即办理在线改签（同向席位）") {
                        dialog.dismiss()
                        startActivity(Intent(this, ChangeTicketActivity::class.java).apply {
                            putExtra("originalOrder", order)
                            putExtra("order", order)
                            putExtra("orderId", order.id)
                        })
                    })
                    container.addView(IosBottomSheetHelper.createSecondaryButton(this, "🚄 查询同区间后续可售全部车次") {
                        dialog.dismiss()
                        startActivity(Intent(this, AdvancedSearchResultsActivity::class.java).apply {
                            putExtra("departureStation", order.departureStation)
                            putExtra("arrivalStation", order.arrivalStation)
                            putExtra("departureDate", order.departureDate)
                        })
                    })
                }
            }
            TravelRecoveryOption.Type.REIMBURSEMENT -> startActivity(Intent(this, ElectronicInvoiceActivity::class.java).putExtra("orderId", order.id))
        }
    }

    private fun cardParams(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
    }
}
