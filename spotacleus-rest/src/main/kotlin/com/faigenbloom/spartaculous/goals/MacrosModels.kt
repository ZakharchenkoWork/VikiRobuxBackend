package com.faigenbloom.spartaculous.goals

import kotlinx.serialization.Serializable

@Serializable
data class MacrosGoalDto(
    val proteinGrams: String? = null,
    val fatGrams: String? = null,
    val carbsGrams: String? = null
)

@Serializable
data class UpdateMacrosGoalRequest(
    val proteinGrams: String? = null,
    val fatGrams: String? = null,
    val carbsGrams: String? = null
)
