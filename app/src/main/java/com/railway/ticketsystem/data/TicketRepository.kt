package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Ticket

class TicketRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("ticket_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveTicket(ticket: Ticket) {
        val tickets = getBookedTickets().toMutableList()
        tickets.add(ticket)
        val json = gson.toJson(tickets)
        prefs.edit().putString("booked_tickets", json).apply()
    }
    
    fun getBookedTickets(): List<Ticket> {
        val json = prefs.getString("booked_tickets", null)
        return if (json != null) {
            val type = object : TypeToken<List<Ticket>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun clearAllTickets() {
        prefs.edit().remove("booked_tickets").apply()
    }
}