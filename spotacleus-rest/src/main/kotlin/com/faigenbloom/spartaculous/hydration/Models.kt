package com.faigenbloom.spartaculous.hydration

import kotlinx.serialization.Serializable

@Serializable
data class HydrationDto(
    val dateEpochMillis: Long,   // startOfDay in user's local TZ from client
    val liters: Double  // 0.0 .. 10.0, step 0.1
)
