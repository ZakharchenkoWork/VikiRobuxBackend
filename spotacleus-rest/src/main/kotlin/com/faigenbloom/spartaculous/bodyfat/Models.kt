package com.faigenbloom.spartaculous.bodyfat

import kotlinx.serialization.Serializable

@Serializable
data class BodyFatEntryDto(
    val id: String,
    val percentValue: Float,
    val recordedAtEpochMillis: Long
)

@Serializable
data class CreateBodyFatEntryRequest(
    val percentValue: Float,
    val recordedAtEpochMillis: Long? = null
)
