package com.railway.ticketsystem.data

import com.google.gson.Gson
import com.railway.ticketsystem.model.Order
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class OrderPaymentRepositoryTest {
    private lateinit var preferences: InMemoryPreferences
    private lateinit var repository: OrderRepository

    @Before
    fun setUp() {
        preferences = InMemoryPreferences()
        repository = OrderRepository(preferences)
    }

    @Test
    fun paymentJustBeforeDeadlinePaysEveryCompanionExactlyOnce() {
        val companions = listOf(
            ticket("one", "01A").copy(groupId = "group", groupPassengerCount = 2),
            ticket("two", "01B").copy(groupId = "group", groupPassengerCount = 2)
        )
        assertTrue(repository.saveOrdersIfSeatsAvailable(companions))

        val paid = repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE - 1)!!
        assertEquals(2, paid.size)
        assertTrue(repository.getAllOrders().all { it.status == "已支付" && it.payTime == PAY_TIME })
        assertTrue(repository.getAllOrders().all { it.paymentEffectsPending })
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, "second attempt", DEADLINE - 1))
        assertNull(repository.cancelPendingPaymentBatch(BATCH, USER))
        assertTrue(repository.expirePendingPayments(DEADLINE + 1).isEmpty())
        assertTrue(repository.getAllOrders().all { it.payTime == PAY_TIME })
    }

    @Test
    fun exactDeadlineRejectsPaymentAndExpiresWholeTransfer() {
        val legs = listOf(
            ticket("first", "01A").copy(itineraryId = "transfer"),
            ticket("second", "02A").copy(trainNumber = "G102", itineraryId = "transfer")
        )
        assertTrue(repository.saveOrdersIfSeatsAvailable(legs))
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE))
        assertEquals(2, repository.expirePendingPayments(DEADLINE).size)
        assertTrue(repository.getAllOrders().all { it.status == "已取消" && it.inventoryReleasePending })
        assertTrue(repository.getAllOrders().all { !it.cancelReason.isNullOrBlank() })
        assertTrue(repository.expirePendingPayments(DEADLINE + 1).isEmpty())
        assertNull(repository.cancelPendingPaymentBatch(BATCH, USER))
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE - 1))
    }

    @Test
    fun noExpirationBeforeDeadlineAndUnrelatedBatchRemainsPending() {
        seed(ticket("one", "01A"), ticket("later", "01B").copy(paymentBatchId = "later", paymentDeadlineMillis = DEADLINE + 10_000))
        assertTrue(repository.expirePendingPayments(DEADLINE - 1).isEmpty())
        assertEquals(listOf("one"), repository.expirePendingPayments(DEADLINE).map { it.id })
        assertEquals("待支付", repository.getOrderById("later")!!.status)
    }

    @Test
    fun allBatchDeadlinesAreCheckedAndOneExpiredLegCancelsEntireBatch() {
        seed(ticket("later-first", "01A").copy(paymentDeadlineMillis = DEADLINE + 5_000), ticket("expired-second", "01B"))
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE))
        assertEquals(2, repository.expirePendingPayments(DEADLINE).size)
        assertTrue(repository.getAllOrders().all { it.status == "已取消" })
    }

    @Test
    fun wrongUserCannotPayOrCancelAnotherUsersBatch() {
        assertTrue(repository.saveOrder(ticket("one", "01A")))
        assertNull(repository.payPendingPaymentBatch(BATCH, "other-user", PAY_TIME, DEADLINE - 1))
        assertNull(repository.cancelPendingPaymentBatch(BATCH, "other-user"))
        assertEquals("待支付", repository.getOrderById("one")!!.status)
    }

    @Test
    fun mixedStatusBatchCannotBePartiallyPaid() {
        seed(ticket("pending", "01A"), ticket("cancelled", "01B").copy(status = "已取消"))
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE - 1))
        assertEquals("待支付", repository.getOrderById("pending")!!.status)
        assertEquals("已取消", repository.getOrderById("cancelled")!!.status)
    }

    @Test
    fun cancellationIsAnExactlyOnceWholeBatchTransition() {
        assertTrue(repository.saveOrdersIfSeatsAvailable(listOf(ticket("one", "01A"), ticket("two", "01B"))))
        assertEquals(2, repository.cancelPendingPaymentBatch(BATCH, USER)!!.size)
        assertTrue(repository.getAllOrders().all { it.status == "已取消" && it.inventoryReleasePending })
        assertNull(repository.cancelPendingPaymentBatch(BATCH, USER))
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE - 1))
        assertTrue(repository.expirePendingPayments(DEADLINE).isEmpty())
    }

    @Test
    fun failedPaymentCommitLeavesEveryTicketPendingAndCanRetry() {
        assertTrue(repository.saveOrdersIfSeatsAvailable(listOf(ticket("one", "01A"), ticket("two", "01B"))))
        preferences.failNextCommit = true
        assertNull(repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE - 1))
        assertTrue(repository.getAllOrders().all { it.status == "待支付" && it.payTime == null })
        assertEquals(2, repository.payPendingPaymentBatch(BATCH, USER, PAY_TIME, DEADLINE - 1)!!.size)
    }

    @Test
    fun failedExpirationCommitDoesNotReleaseAnyTicketAndCanRetry() {
        assertTrue(repository.saveOrdersIfSeatsAvailable(listOf(ticket("one", "01A"), ticket("two", "01B"))))
        preferences.failNextCommit = true
        assertTrue(repository.expirePendingPayments(DEADLINE).isEmpty())
        assertTrue(repository.getAllOrders().all { it.status == "待支付" && !it.inventoryReleasePending })
        assertEquals(2, repository.expirePendingPayments(DEADLINE).size)
    }

    @Test
    fun pendingSeatBlocksNewBookingAndCancellationFreesSeatIdentity() {
        assertTrue(repository.saveOrder(ticket("one", "01A")))
        val competitor = ticket("two", "01A").copy(paymentBatchId = "another")
        assertFalse(repository.saveOrder(competitor))
        assertNotNull(repository.cancelPendingPaymentBatch(BATCH, USER))
        assertTrue(repository.saveOrder(competitor))
    }

    @Test
    fun conflictInSecondTicketRejectsWholeIncomingBatch() {
        assertTrue(repository.saveOrder(ticket("existing", "01B").copy(paymentBatchId = "existing")))
        assertFalse(repository.saveOrdersIfSeatsAvailable(listOf(ticket("one", "01A"), ticket("two", "01B"))))
        assertEquals(listOf("existing"), repository.getAllOrders().map { it.id })
    }

    @Test
    fun duplicateIdsWithinIncomingBatchCannotBePersisted() {
        assertFalse(repository.saveOrdersIfSeatsAvailable(listOf(ticket("same", "01A"), ticket("same", "01B"))))
        assertTrue(repository.getAllOrders().isEmpty())
    }

    @Test
    fun stalePaidOrderCannotReviveRefundedTicket() {
        val paid = ticket("one", "01A").copy(status = "已支付", payTime = PAY_TIME)
        assertTrue(repository.saveOrder(paid))
        assertTrue(repository.updateOrderStatus(paid.id, USER, "已取消"))
        assertFalse(repository.updateOrderStatus(paid.id, USER, "已取消"))
        assertFalse(repository.replaceOrderIfSeatAvailable(paid.id, USER, paid.copy(trainNumber = "G999")))
        assertEquals("已取消", repository.getOrderById(paid.id)!!.status)
    }

    @Test
    fun pendingOrderCannotSkipPaymentThroughTicketChange() {
        val pending = ticket("one", "01A")
        assertTrue(repository.saveOrder(pending))
        assertFalse(repository.replaceOrderIfSeatAvailable(pending.id, USER, pending.copy(status = "已支付", trainNumber = "G999")))
        assertEquals("待支付", repository.getOrderById(pending.id)!!.status)
    }

    private fun seed(vararg orders: Order) {
        preferences.edit().putString("orders", Gson().toJson(orders.toList())).commit()
    }

    @Test
    fun legacyPendingOrderReceivesPersistentBatchAndOriginalDeadline() {
        val legacy = ticket("legacy", "01A").copy(paymentBatchId = null, paymentDeadlineMillis = 0)
        seed(legacy)
        val migrated = repository.getOrderById(legacy.id)!!
        val expectedDeadline = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(legacy.createTime)!!.time + 900_000L
        assertFalse(migrated.paymentBatchId.isNullOrBlank())
        assertEquals(expectedDeadline, migrated.paymentDeadlineMillis)
        val reloaded = OrderRepository(preferences).getOrderById(legacy.id)!!
        assertEquals(migrated.paymentBatchId, reloaded.paymentBatchId)
        assertEquals(expectedDeadline, reloaded.paymentDeadlineMillis)
    }

    @Test
    fun malformedLegacyTimeCannotCreateAnUnlimitedSeatHold() {
        seed(ticket("legacy", "01A").copy(paymentBatchId = null, paymentDeadlineMillis = 0, createTime = "not a date"))
        assertEquals(1, repository.expirePendingPayments(System.currentTimeMillis()).size)
        assertEquals("已取消", repository.getOrderById("legacy")!!.status)
    }

    private fun ticket(id: String, seat: String) = Order(
        id = id, userId = USER, trainNumber = "G101", departureStation = "北京南", arrivalStation = "上海虹桥",
        departureTime = "08:00", arrivalTime = "12:30", departureDate = "2033-05-18", seatType = "二等座",
        seatNumber = seat, carNumber = "01", passengerName = "测试乘客", passengerIdCard = "test-id-$id",
        passengerPhone = "13800000000", basePrice = 100.0, finalPrice = 100.0, status = "待支付",
        createTime = "2033-05-18 08:00:00", payTime = null, paymentBatchId = BATCH, paymentDeadlineMillis = DEADLINE
    )

    companion object {
        private const val USER = "test-user"
        private const val BATCH = "test-checkout"
        private const val DEADLINE = 2_000_000_900_000L
        private const val PAY_TIME = "2033-05-18 08:10:00"
    }
}
