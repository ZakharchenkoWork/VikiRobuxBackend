package com.faigenbloom.spartaculous.hydration

import kotlinx.serialization.Serializable

@Serializable
data class HydrationDto(
    val dateEpochMillis: Long,   // startOfDay in user's local TZ from client
    val liters: Double  // 0.0 .. 10.0, step 0.1
)

@Serializable
enum class HydrationEventType {
    add,
    edit,
    delete
}

@Serializable
enum class HydrationEventSource {
    manual,
    quick_add,
    edit
}

@Serializable
data class HydrationEventDto(
    val id: String,
    val occurredAtEpochMillis: Long,
    val amountLiters: Double,
    val type: HydrationEventType,
    val createdAt: Long,
    val source: HydrationEventSource? = null
)

@Serializable
data class CreateHydrationEventRequest(
    val occurredAtEpochMillis: Long,
    val amountLiters: Double,
    val source: HydrationEventSource? = null
)

@Serializable
data class UpdateHydrationEventRequest(
    val amountLiters: Double,
    val occurredAtEpochMillis: Long? = null,
    val source: HydrationEventSource? = null
)
