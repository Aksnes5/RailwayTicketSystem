package com.railway.ticketsystem.data

import android.content.Context

/**
 * 统一的仓储单例管理与依赖注入容器（Service Locator）。
 * 保证全应用共享同一数据缓存、并发锁与生命周期，杜绝 Activity 级重复构造与 Context 泄漏。
 */
object AppRepositoryProvider {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }

    val userRepository: UserRepository by lazy {
        checkInitialized()
        UserRepository(appContext)
    }

    val orderRepository: OrderRepository by lazy {
        checkInitialized()
        OrderRepository(appContext)
    }

    val passengerRepository: PassengerRepository by lazy {
        checkInitialized()
        PassengerRepository(appContext)
    }

    val ticketRepository: TicketRepository by lazy {
        checkInitialized()
        TicketRepository(appContext)
    }

    val seatInventoryRepository: SeatInventoryRepository by lazy {
        checkInitialized()
        SeatInventoryRepository(appContext)
    }

    val waitlistRepository: WaitlistRepository by lazy {
        checkInitialized()
        WaitlistRepository(appContext)
    }

    val messageRepository: MessageRepository by lazy {
        checkInitialized()
        MessageRepository(appContext)
    }

    val membershipRepository: MembershipRepository by lazy {
        checkInitialized()
        MembershipRepository(appContext)
    }

    val offlineTravelRepository: OfflineTravelRepository by lazy {
        checkInitialized()
        OfflineTravelRepository(appContext)
    }

    val tripProgressRepository: TripProgressRepository by lazy {
        checkInitialized()
        TripProgressRepository(appContext)
    }

    private fun checkInitialized() {
        check(::appContext.isInitialized) {
            "AppRepositoryProvider 尚未在 Application 中初始化，请先调用 initialize(context)"
        }
    }
}
