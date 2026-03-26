package com.faigenbloom.spartaculous.measurements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementType {
    @SerialName("chest") CHEST,
    @SerialName("waist") WAIST,
    @SerialName("hips") HIPS,
    @SerialName("biceps") BICEPS,
    @SerialName("thigh") THIGH,
    @SerialName("calf") CALF,
    @SerialName("neck") NECK,
    @SerialName("shoulders") SHOULDERS
}

@Serializable
data class MeasurementEntryDto(
    val id: String,
    val type: MeasurementType,
    val valueCm: Float,
    // Всегда миллисекунды с нормализацией к началу суток UTC
    val dateEpochMillis: Long
)

@Serializable
data class MeasurementUpsertDto(
    val type: MeasurementType,
    val valueCm: Float,
    // Любые ms; мы нормализуем к началу суток UTC при апсерте
    val dateEpochMillis: Long
)

@Serializable
data class MeasurementsListResponse(
    val items: List<MeasurementEntryDto>
)

@Serializable
data class MeasurementsGoalDto(
    val enabled: Boolean,
    val type: MeasurementType,
    val targetValueCm: Float
)

@Serializable
data class MeasurementGoal(
    val type: MeasurementType,
    val targetValueCm: Float,
    val deadlineEpochMillis: Long? = null,
    val enabled: Boolean = true,
    val updatedAtEpochMillis: Long? = null
)

@Serializable
data class MeasurementGoalsResponse(
    val items: List<MeasurementGoal>
)

@Serializable
data class MeasurementGoalUpsertDto(
    val type: MeasurementType,
    val targetValueCm: Float,
    val deadlineEpochMillis: Long? = null,
    val enabled: Boolean? = null
)
