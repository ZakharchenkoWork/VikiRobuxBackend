package com.faigenbloom.spartaculous.training

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseMode {
    StrengthWeighted,
    StrengthBodyweight,
    StrengthAssisted,
    CardioTime,
    CardioDistance,
    HIIT,
    Circuit,
    Mobility,
    Core,
    Functional,
    Sport,
    Combat,
    Outdoor,
    Custom
}

@Serializable
data class ExerciseMetricsDto(
    val mode: ExerciseMode = ExerciseMode.Custom,
    val supportsSets: Boolean = false,
    val supportsReps: Boolean = false,
    val supportsWeight: Boolean = false,
    val supportsExtraLoad: Boolean = false,
    val supportsDuration: Boolean = false,
    val supportsDistance: Boolean = false,
    val supportsLevel: Boolean = false,
    val supportsTempo: Boolean = false,
    val supportsIntervals: Boolean = false,
    val supportsRestTimer: Boolean = false
)

@Serializable
data class ExerciseRepRangeDto(
    val min: Int,
    val max: Int
)

@Serializable
data class ExerciseDefaultSettingsDto(
    val repRange: ExerciseRepRangeDto? = null,
    val durationStepSec: Int? = null,
    val distanceUnit: String? = null,
    val weightUnit: String? = null
)

@Serializable
data class TrainingDetailDto(
    val reps: Int = 0,
    val weightKg: Int = 0,
    val durationMin: Int = 0
)

@Serializable
data class TrainingSummaryDto(
    val sets: Int,
    val reps: Int,
    val weightKg: Int,
    val durationMin: Int
)

@Serializable
data class TrainingEntryDto(
    val id: String,
    val exerciseKey: String,
    val name: String,
    val recordedAtEpochMillis: Long,
    val details: List<TrainingDetailDto>,
    val summary: TrainingSummaryDto
)

@Serializable
data class CreateTrainingEntryRequest(
    val exerciseKey: String,
    val name: String,
    val recordedAtEpochMillis: Long? = null,
    val details: List<TrainingDetailDto> = emptyList()
)

@Serializable
data class UpdateTrainingEntryRequest(
    val exerciseKey: String? = null,
    val name: String? = null,
    val recordedAtEpochMillis: Long? = null,
    val details: List<TrainingDetailDto>? = null
)

@Serializable
data class TrainingExerciseDto(
    val key: String,
    val name: String,
    val category: String,
    val iconKey: IconKey? = null,
    val source: String = "custom", // "system" | "custom"
    val metrics: ExerciseMetricsDto,
    val defaultSettings: ExerciseDefaultSettingsDto? = null,
    val overridesSystemKey: String? = null
)

@Serializable
data class CreateTrainingExerciseRequest(
    val name: String,
    val category: String, // "Strength" | "Cardio"
    val iconKey: IconKey? = null,
    val key: String? = null,
    val metrics: ExerciseMetricsDto? = null,
    val defaultSettings: ExerciseDefaultSettingsDto? = null
)

@Serializable
data class UpdateTrainingExerciseRequest(
    val name: String? = null,
    val category: String? = null,
    val iconKey: IconKey? = null,
    val metrics: ExerciseMetricsDto? = null,
    val defaultSettings: ExerciseDefaultSettingsDto? = null
)

// ========== Training Plan Templates ==========

@Serializable
data class TrainingPlanTemplateItemDto(
    val exerciseKey: String,
    val name: String,
    val order: Int
)

@Serializable
data class TrainingPlanTemplateDto(
    val id: String,
    val name: String,
    val items: List<TrainingPlanTemplateItemDto>,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TrainingPlanTemplateListResponse(
    val templates: List<TrainingPlanTemplateDto>
)

@Serializable
data class CreateTrainingPlanTemplateRequest(
    val name: String,
    val items: List<TrainingPlanTemplateItemDto>
)

@Serializable
data class UpdateTrainingPlanTemplateRequest(
    val name: String,
    val items: List<TrainingPlanTemplateItemDto>
)

@Serializable
data class ApplyTemplateToDayRequest(
    val date: String,
    val replaceExisting: Boolean = false
)

// ========== Day Plans ==========

@Serializable
data class DayPlanExerciseDto(
    val exerciseKey: String,
    val name: String,
    val order: Int
)

@Serializable
data class DayPlanDto(
    val date: String,
    val items: List<DayPlanExerciseDto>,
    val sourceTemplateId: String? = null,
    val updatedAt: String
)

@Serializable
data class DayPlanResponse(
    val plan: DayPlanDto?
)

@Serializable
data class DayPlansResponse(
    val plans: List<DayPlanDto>
)

@Serializable
data class UpdateDayPlanRequest(
    val items: List<DayPlanExerciseDto>,
    val sourceTemplateId: String? = null
)

// ========== API Errors ==========

@Serializable
data class ApiError(
    val error: String,
    val message: String,
    val details: Map<String, String>? = null
)
