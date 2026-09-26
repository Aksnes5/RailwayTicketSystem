package com.railway.ticketsystem.model

import java.io.Serializable

enum class TicketType(val label: String, val discountRate: Double, val badgeColor: String) {
    ADULT("成人票", 1.0, "#0A84FF"),
    CHILD("儿童票", 0.5, "#34C759"),
    STUDENT("学生票", 0.75, "#FF9500"),
    DISABLED_MILITARY("残军票", 0.5, "#AF52DE");

    companion object {
        fun fromLabel(label: String?): TicketType {
            if (label.isNullOrEmpty()) return ADULT
            return values().firstOrNull { it.label == label } ?: ADULT
        }
    }
}

data class Passenger(
    val id: String,
    val userId: String,
    val name: String,
    val idCard: String,
    val phone: String,
    val addTime: String = "",
    val ticketType: String = TicketType.ADULT.label
) : Serializable
