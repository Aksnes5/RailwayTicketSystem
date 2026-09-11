package com.railway.ticketsystem.model

import java.io.Serializable

/** Physical service network a station entry belongs to. The same city may have separate entries. */
enum class StationNetwork {
    HIGH_SPEED,
    CONVENTIONAL
}

data class Station(
    val name: String,
    val code: String,
    val city: String = "",
    val network: StationNetwork = StationNetwork.HIGH_SPEED
) : Serializable
