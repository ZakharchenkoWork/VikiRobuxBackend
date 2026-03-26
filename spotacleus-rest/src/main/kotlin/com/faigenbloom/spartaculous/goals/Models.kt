package com.faigenbloom.spartaculous.goals

import kotlinx.serialization.Serializable

@Serializable
data class GoalsOverviewDto(
    val weightTarget: String? = null,
    val weightDateEpochMillis: Long? = null,
    val caloriesTarget: String? = null,
    val caloriesEatenToday: Int = 0,
    val bodyFatTarget: String? = null,
    val bodyFatDateEpochMillis: Long? = null,
    val trainingWeekCount: Int = 0,
    val recoveryPercent: Int = 0,
    val recommendations: List<String> = emptyList(),
    val analytics: List<String> = emptyList()
)

@Serializable
data class UpdateWeightGoalRequest(
    val target: String,
    val dateEpochMillis: Long? = null
)

@Serializable
data class UpdateCaloriesGoalRequest(
    val target: String,
    val dateEpochMillis: Long? = null
)

@Serializable
data class UpdateBodyFatGoalRequest(
    val target: String,
    val dateEpochMillis: Long? = null
)

// Training goals
@Serializable
enum class TrainingGoalType { SESSIONS_PER_WEEK, MINUTES_PER_WEEK, EXERCISE_PR }

@Serializable
data class TrainingGoalDto(
    val type: TrainingGoalType,
    val sessionsPerWeek: String? = null,
    val minutesPerWeek: String? = null,
    val exerciseName: String? = null,
    val exerciseWeight: String? = null,
    val exerciseReps: String? = null,
    val progressThisWeek: Int = 0
)

@Serializable
data class UpdateTrainingGoalRequest(
    val type: TrainingGoalType,
    val sessionsPerWeek: String? = null,
    val minutesPerWeek: String? = null,
    val exerciseName: String? = null,
    val exerciseWeight: String? = null,
    val exerciseReps: String? = null
)

// Recovery goals and logs
@Serializable
data class RecoveryGoalsDto(
    val sleepEnabled: Boolean = false,
    val sleepHours: String? = null,
    val restDaysEnabled: Boolean = false,
    val restDays: String? = null,
    val mobilityEnabled: Boolean = false,
    val mobilityMinutes: String? = null,
    val mindfulnessEnabled: Boolean = false,
    val mindfulnessMinutes: String? = null
)

@Serializable
data class RecoveryLogItem(
    val dateEpochMillis: Long,
    val sleepHours: Int = 0,
    val restDay: Boolean = false,
    val mobilityMinutes: Int = 0,
    val mindfulnessMinutes: Int = 0
)

@Serializable
data class RecoveryLogsResponse(
    val items: List<RecoveryLogItem>
)

@Serializable
data class RecoveryLogRequest(
    val dateEpochMillis: Long? = null,
    val sleepHours: Int = 0,
    val restDay: Boolean = false,
    val mobilityMinutes: Int = 0,
    val mindfulnessMinutes: Int = 0
)

// Calories logs
@Serializable
data class CaloriesLogItem(
    val dateEpochMillis: Long,
    val eatenCalories: Int
)

@Serializable
data class CaloriesLogsResponse(
    val items: List<CaloriesLogItem>
)

@Serializable
data class CaloriesLogRequest(
    val dateEpochMillis: Long? = null,
    val eatenCalories: Int
)

// Training plan and templates
@Serializable
data class DayDto(
    val enabled: Boolean,
    val session: String
)

@Serializable
data class TrainingPlanDto(
    val template: String,
    val days: List<DayDto>
)

@Serializable
data class TemplateSummaryDto(
    val id: String,
    val name: String
)

@Serializable
data class TemplateListDto(
    val items: List<TemplateSummaryDto>
)

@Serializable
data class CreateTemplateRequest(
    val name: String,
    val days: List<DayDto>
)

@Serializable
data class CreateTemplateResponse(
    val id: String,
    val name: String
)

@Serializable
data class UpdateTemplateRequest(
    val name: String,
    val days: List<DayDto>
)

// Recommendations
@Serializable
data class RecommendationDto(
    val id: String,
    val text: String
)

@Serializable
data class RecommendationsResponse(
    val items: List<RecommendationDto>
)

// Analytics
@Serializable
data class AnalyticsDto(
    val range: String,
    val lines: List<String>
)
