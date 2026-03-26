package com.faigenbloom.spartaculous.analytics

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val values: List<Double>,
    val color: String
)

@Serializable
data class MicroDto(
    val key: String,
    val progress: Double
)

@Serializable
data class AnalyticsDashboardDto(
    val weightCalories: List<SeriesDto>,
    val trainingStability: List<Int>,
    val trainingMinutes: List<Int>,
    val macros: List<SeriesDto>,
    val micros: List<MicroDto>,
    val water: List<SeriesDto>,
    val sleep: List<SeriesDto>,
    val recovery: List<SeriesDto>,
    val measurements: List<SeriesDto>? = null
)
