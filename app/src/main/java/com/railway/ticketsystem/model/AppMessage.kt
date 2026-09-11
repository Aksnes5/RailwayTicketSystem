package com.railway.ticketsystem.model

import java.io.Serializable

/** An immutable business event in the user's inbox, independent of push settings. */
data class AppMessage(
    val id: String,
    val userId: String,
    val category: String,
    val title: String,
    val content: String,
    val createdAt: String,
    val createdAtMillis: Long,
    val relatedOrderId: String? = null,
    val isRead: Boolean = false,
    val eventKey: String? = null,
    val relatedWaitlistId: String? = null
) : Serializable
