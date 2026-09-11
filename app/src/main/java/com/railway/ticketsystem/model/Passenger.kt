package com.railway.ticketsystem.model

import java.io.Serializable

data class Passenger(
    val id: String,
    val userId: String,
    val name: String,
    val idCard: String,
    val phone: String,
    val addTime: String = ""
) : Serializable




