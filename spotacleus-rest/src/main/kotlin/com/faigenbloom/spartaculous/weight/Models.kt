package com.faigenbloom.spartaculous.weight

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class WeightEntryDto(
    val id: String,
    val valueKg: Float,
    val recordedAtEpochMillis: Long
)

@Serializable
data class AddWeightRequest(
    val valueKg: Float,
    val recordedAtEpochMillis: Long? = null
)
