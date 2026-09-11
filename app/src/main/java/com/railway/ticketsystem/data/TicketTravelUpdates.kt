package com.railway.ticketsystem.data

import android.content.Context
import com.railway.ticketsystem.model.Order

/** Local demo data for station gate updates; it never claims to be a real station feed. */
object TicketTravelUpdates {
    /*
     * Gate information is only a disposable display cache. Keeping it out of the
     * encrypted order store ensures a broken/old encrypted preference never hides an
     * otherwise valid local ticket.
     */
    private const val PREFS = "ticket_travel_updates_cache"

    fun getGate(context: Context, order: Order): String = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("gate_${order.id}", null)
            ?: defaultGate(order)
    }.getOrDefault(defaultGate(order))

    private fun defaultGate(order: Order): String = gates[(order.id.hashCode().toUInt().toLong() % gates.size).toInt()]
    private val gates = listOf("A1", "A3", "B2", "B5", "C1", "C4")
}
