package com.faigenbloom.spartaculous.goals

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.litote.kmongo.SetTo
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

class MongoGoalsRepository(private val db: CoroutineDatabase) : GoalsRepository {
    // Collections
    private val coreCol: CoroutineCollection<CoreStateDoc> = db.getCollection("goals_core")
    private val trainingCol: CoroutineCollection<TrainingGoalDoc> = db.getCollection("goals_training")
    private val recoveryGoalsCol: CoroutineCollection<RecoveryGoalsDoc> = db.getCollection("goals_recovery")
    private val recoveryLogsCol: CoroutineCollection<RecoveryLogDoc> = db.getCollection("goals_recovery_logs")
    private val caloriesLogsCol: CoroutineCollection<CaloriesLogDoc> = db.getCollection("goals_calories_logs")
    private val macrosCol: CoroutineCollection<MacrosGoalDoc> = db.getCollection("goals_macros")
    private val planCol: CoroutineCollection<TrainingPlanDoc> = db.getCollection("goals_plan")
    private val templatesCol: CoroutineCollection<TemplateDoc> = db.getCollection("goals_templates")

    // Overview
    override fun getOverview(userId: String): GoalsOverviewDto = kotlinx.coroutines.runBlocking {
        val core = coreCol.find(CoreStateDoc::userId eq userId).limit(1).toList().firstOrNull()
        val tg = trainingCol.find(TrainingGoalDoc::userId eq userId).limit(1).toList().firstOrNull()
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val eatenToday = caloriesLogsCol.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("dateEpochMillis", todayStart)
            )
        ).limit(1).toList().firstOrNull()?.eatenCalories ?: 0
        GoalsOverviewDto(
            weightTarget = core?.weightTarget,
            weightDateEpochMillis = core?.weightDateEpochMillis,
            caloriesTarget = core?.caloriesTarget,
            caloriesEatenToday = eatenToday,
            bodyFatTarget = core?.bodyFatTarget,
            bodyFatDateEpochMillis = core?.bodyFatDateEpochMillis,
            trainingWeekCount = tg?.progressThisWeek ?: 0,
            recoveryPercent = 0,
            recommendations = emptyList(),
            analytics = emptyList()
        )
    }

    override fun updateWeightGoal(userId: String, target: String, dateEpochMillis: Long?) {
        if (!target.isNumericString()) throw IllegalArgumentException("Invalid target: must be numeric string")
        kotlinx.coroutines.runBlocking {
            val doc = coreCol.find(CoreStateDoc::userId eq userId).limit(1).toList().firstOrNull()
            val updated = (doc ?: CoreStateDoc(userId = userId)).copy(
                weightTarget = target,
                weightDateEpochMillis = dateEpochMillis
            )
            coreCol.replaceOne(Filters.eq("userId", userId), updated, ReplaceOptions().upsert(true))
        }
    }

    override fun updateCaloriesGoal(userId: String, target: String, dateEpochMillis: Long?) {
        if (!target.isNumericString()) throw IllegalArgumentException("Invalid target: must be numeric string")
        kotlinx.coroutines.runBlocking {
            val doc = coreCol.find(CoreStateDoc::userId eq userId).limit(1).toList().firstOrNull()
            val updated = (doc ?: CoreStateDoc(userId = userId)).copy(
                caloriesTarget = target,
                caloriesDateEpochMillis = dateEpochMillis
            )
            coreCol.replaceOne(Filters.eq("userId", userId), updated, ReplaceOptions().upsert(true))
        }
    }

    override fun updateBodyFatGoal(userId: String, target: String, dateEpochMillis: Long?) {
        // Validate target can be parsed as float in range 3.0 - 60.0
        val value = target.toFloatOrNull() ?: throw IllegalArgumentException("Invalid target: must be numeric string")
        if (value < 3.0f || value > 60.0f) {
            throw IllegalArgumentException("Body fat target must be between 3.0 and 60.0")
        }
        kotlinx.coroutines.runBlocking {
            val doc = coreCol.find(CoreStateDoc::userId eq userId).limit(1).toList().firstOrNull()
            val updated = (doc ?: CoreStateDoc(userId = userId)).copy(
                bodyFatTarget = target,
                bodyFatDateEpochMillis = dateEpochMillis
            )
            coreCol.replaceOne(Filters.eq("userId", userId), updated, ReplaceOptions().upsert(true))
        }
    }

    // Training goals
    override fun getTrainingGoal(userId: String): TrainingGoalDto = kotlinx.coroutines.runBlocking {
        val doc = trainingCol.find(TrainingGoalDoc::userId eq userId).limit(1).toList().firstOrNull()
        if (doc == null) {
            val def = TrainingGoalDto(
                type = TrainingGoalType.SESSIONS_PER_WEEK,
                sessionsPerWeek = "3",
                progressThisWeek = 0
            )
            def
        } else doc.toDto()
    }

    override fun updateTrainingGoal(userId: String, req: UpdateTrainingGoalRequest) {
        // Validate
        when (req.type) {
            TrainingGoalType.SESSIONS_PER_WEEK -> {
                if (req.sessionsPerWeek.isNullOrBlank() || !req.sessionsPerWeek.isNumericString())
                    throw IllegalArgumentException("Invalid sessionsPerWeek: must be numeric string")
            }
            TrainingGoalType.MINUTES_PER_WEEK -> {
                if (req.minutesPerWeek.isNullOrBlank() || !req.minutesPerWeek.isNumericString())
                    throw IllegalArgumentException("Invalid minutesPerWeek: must be numeric string")
            }
            TrainingGoalType.EXERCISE_PR -> {
                if (req.exerciseName.isNullOrBlank()) throw IllegalArgumentException("exerciseName is required")
                if (req.exerciseWeight.isNullOrBlank() || !req.exerciseWeight.isNumericString())
                    throw IllegalArgumentException("Invalid exerciseWeight: must be numeric string")
                if (req.exerciseReps.isNullOrBlank() || !req.exerciseReps.isNumericString())
                    throw IllegalArgumentException("Invalid exerciseReps: must be numeric string")
            }
        }
        kotlinx.coroutines.runBlocking {
            val prev = trainingCol.find(TrainingGoalDoc::userId eq userId).limit(1).toList().firstOrNull()
            val progress = prev?.progressThisWeek ?: 0
            val toSave = TrainingGoalDoc(
                userId = userId,
                type = req.type,
                sessionsPerWeek = req.sessionsPerWeek,
                minutesPerWeek = req.minutesPerWeek,
                exerciseName = req.exerciseName,
                exerciseWeight = req.exerciseWeight,
                exerciseReps = req.exerciseReps,
                progressThisWeek = progress
            )
            trainingCol.replaceOne(Filters.eq("userId", userId), toSave, ReplaceOptions().upsert(true))
        }
    }

    // Recovery goals and logs
    override fun getRecoveryGoals(userId: String): RecoveryGoalsDto = kotlinx.coroutines.runBlocking {
        recoveryGoalsCol.find(RecoveryGoalsDoc::userId eq userId).limit(1).toList().firstOrNull()?.toDto() ?: RecoveryGoalsDto()
    }

    override fun updateRecoveryGoals(userId: String, dto: RecoveryGoalsDto) {
        fun validateNumericIfEnabled(enabled: Boolean, v: String?, field: String) {
            if (enabled) {
                if (v.isNullOrBlank() || !v.isNumericString()) throw IllegalArgumentException("Invalid ${'$'}field: must be numeric string")
            }
        }
        validateNumericIfEnabled(dto.sleepEnabled, dto.sleepHours, "sleepHours")
        validateNumericIfEnabled(dto.restDaysEnabled, dto.restDays, "restDays")
        validateNumericIfEnabled(dto.mobilityEnabled, dto.mobilityMinutes, "mobilityMinutes")
        validateNumericIfEnabled(dto.mindfulnessEnabled, dto.mindfulnessMinutes, "mindfulnessMinutes")
        kotlinx.coroutines.runBlocking {
            val doc = RecoveryGoalsDoc.fromDto(userId, dto)
            recoveryGoalsCol.replaceOne(Filters.eq("userId", userId), doc, ReplaceOptions().upsert(true))
        }
    }

    override fun getRecoveryLogs(userId: String, fromEpochMillis: Long, toEpochMillis: Long): RecoveryLogsResponse = kotlinx.coroutines.runBlocking {
        if (toEpochMillis < fromEpochMillis) throw IllegalArgumentException("Invalid range: to < from")
        val items = recoveryLogsCol.find(Filters.and(
            Filters.eq("userId", userId),
            Filters.gte("dateEpochMillis", fromEpochMillis),
            Filters.lte("dateEpochMillis", toEpochMillis)
        )).toList().sortedBy { it.dateEpochMillis }.map { it.toDto() }
        RecoveryLogsResponse(items)
    }

    override fun putRecoveryLog(userId: String, dateEpochMillis: Long, req: RecoveryLogRequest) {
        val effDate = req.dateEpochMillis ?: dateEpochMillis
        val doc = RecoveryLogDoc(
            userId = userId,
            dateEpochMillis = effDate,
            sleepHours = req.sleepHours.coerceAtLeast(0),
            restDay = req.restDay,
            mobilityMinutes = req.mobilityMinutes.coerceAtLeast(0),
            mindfulnessMinutes = req.mindfulnessMinutes.coerceAtLeast(0)
        )
        kotlinx.coroutines.runBlocking {
            recoveryLogsCol.replaceOne(
                Filters.and(Filters.eq("userId", userId), Filters.eq("dateEpochMillis", doc.dateEpochMillis)),
                doc,
                ReplaceOptions().upsert(true)
            )
        }
    }

    override fun getCaloriesLogs(userId: String, fromEpochMillis: Long, toEpochMillis: Long): CaloriesLogsResponse = kotlinx.coroutines.runBlocking {
        if (toEpochMillis < fromEpochMillis) throw IllegalArgumentException("Invalid range: to < from")
        val items = caloriesLogsCol.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.gte("dateEpochMillis", fromEpochMillis),
                Filters.lte("dateEpochMillis", toEpochMillis)
            )
        ).toList().sortedBy { it.dateEpochMillis }.map { it.toDto() }
        CaloriesLogsResponse(items)
    }

    override fun putCaloriesLog(userId: String, dateEpochMillis: Long, req: CaloriesLogRequest) {
        val effDate = req.dateEpochMillis ?: dateEpochMillis
        val doc = CaloriesLogDoc(
            userId = userId,
            dateEpochMillis = effDate,
            eatenCalories = req.eatenCalories.coerceAtLeast(0)
        )
        kotlinx.coroutines.runBlocking {
            caloriesLogsCol.replaceOne(
                Filters.and(Filters.eq("userId", userId), Filters.eq("dateEpochMillis", doc.dateEpochMillis)),
                doc,
                ReplaceOptions().upsert(true)
            )
        }
    }

    // Macros goals
    override fun getMacrosGoal(userId: String): MacrosGoalDto? = kotlinx.coroutines.runBlocking {
        val doc = macrosCol.find(MacrosGoalDoc::userId eq userId).limit(1).toList().firstOrNull()
        if (doc == null) null else {
            val hasAny = (doc.proteinGrams != null) || (doc.fatGrams != null) || (doc.carbsGrams != null)
            if (!hasAny) null else MacrosGoalDto(
                proteinGrams = doc.proteinGrams?.toString(),
                fatGrams = doc.fatGrams?.toString(),
                carbsGrams = doc.carbsGrams?.toString()
            )
        }
    }

    override fun updateMacrosGoal(userId: String, req: UpdateMacrosGoalRequest) {
        fun sanitize(v: String?): Int? {
            if (v == null) return null
            val t = v.trim()
            val iv = t.toIntOrNull() ?: throw IllegalArgumentException("Invalid value: must be integer 0..400")
            if (iv !in 0..400) throw IllegalArgumentException("Out of range: must be 0..400")
            return iv
        }
        val newProtein = sanitize(req.proteinGrams)
        val newFat = sanitize(req.fatGrams)
        val newCarbs = sanitize(req.carbsGrams)
        kotlinx.coroutines.runBlocking {
            val existing = macrosCol.find(MacrosGoalDoc::userId eq userId).limit(1).toList().firstOrNull()
            val updated = MacrosGoalDoc(
                userId = userId,
                proteinGrams = newProtein ?: existing?.proteinGrams,
                fatGrams = newFat ?: existing?.fatGrams,
                carbsGrams = newCarbs ?: existing?.carbsGrams
            )
            val hasAny = (updated.proteinGrams != null) || (updated.fatGrams != null) || (updated.carbsGrams != null)
            if (!hasAny) {
                // если все поля null — удаляем документ, чтобы GET отдавал 204
                macrosCol.deleteMany(Filters.eq("userId", userId))
            } else {
                macrosCol.replaceOne(Filters.eq("userId", userId), updated, ReplaceOptions().upsert(true))
            }
        }
    }

    // Training plan and templates
    override fun getPlan(userId: String): TrainingPlanDto = kotlinx.coroutines.runBlocking {
        val doc = planCol.find(TrainingPlanDoc::userId eq userId).limit(1).toList().firstOrNull()
        if (doc == null) {
            TrainingPlanDto(
                template = "Push/Pull/Legs",
                days = listOf(
                    DayDto(true, "Upper"),
                    DayDto(true, "Lower"),
                    DayDto(true, "Cardio"),
                    DayDto(true, "Upper"),
                    DayDto(true, "Lower"),
                    DayDto(false, "Rest"),
                    DayDto(false, "Rest")
                )
            )
        } else doc.toDto()
    }

    override fun updatePlan(userId: String, dto: TrainingPlanDto) {
        if (dto.days.size != 7) throw IllegalArgumentException("Plan must contain 7 days")
        val doc = TrainingPlanDoc.fromDto(userId, dto)
        kotlinx.coroutines.runBlocking {
            planCol.replaceOne(Filters.eq("userId", userId), doc, ReplaceOptions().upsert(true))
        }
    }

    override fun listTemplates(userId: String): TemplateListDto = kotlinx.coroutines.runBlocking {
        val list = templatesCol.find(TemplateDoc::userId eq userId).toList().map { TemplateSummaryDto(it._id, it.name) }
        TemplateListDto(list)
    }

    override fun createTemplate(userId: String, req: CreateTemplateRequest): CreateTemplateResponse {
        if (req.name.isBlank()) throw IllegalArgumentException("name is required")
        if (req.days.size != 7) throw IllegalArgumentException("Template must contain 7 days")
        val id = "tpl_" + UUID.randomUUID().toString().take(8)
        val doc = TemplateDoc(
            _id = id,
            userId = userId,
            name = req.name,
            days = req.days.map { DayDoc(it.enabled, it.session) }
        )
        kotlinx.coroutines.runBlocking { templatesCol.insertOne(doc) }
        return CreateTemplateResponse(id = id, name = req.name)
    }

    override fun updateTemplate(userId: String, id: String, req: UpdateTemplateRequest) {
        if (req.name.isBlank()) throw IllegalArgumentException("name is required")
        if (req.days.size != 7) throw IllegalArgumentException("Template must contain 7 days")
        kotlinx.coroutines.runBlocking {
            val existing = templatesCol.find(Filters.and(Filters.eq("userId", userId), Filters.eq("_id", id))).limit(1).toList().firstOrNull()
                ?: throw NoSuchElementException("Template not found")
            val updated = existing.copy(name = req.name, days = req.days.map { DayDoc(it.enabled, it.session) })
            templatesCol.replaceOne(Filters.and(Filters.eq("userId", userId), Filters.eq("_id", id)), updated)
        }
    }

    override fun deleteTemplate(userId: String, id: String) {
        kotlinx.coroutines.runBlocking {
            val res = templatesCol.deleteOne(Filters.and(Filters.eq("userId", userId), Filters.eq("_id", id)))
            if (res.deletedCount == 0L) throw NoSuchElementException("Template not found")
        }
    }

    // Recommendations (legacy for /api/goals/recommendations). Return empty list for now.
    override fun listRecommendations(userId: String): RecommendationsResponse {
        return RecommendationsResponse(items = emptyList())
    }

    override fun ackRecommendation(userId: String, id: String) {
        // no-op in Mongo goals repo; dedicated recommendations feature handles this
    }

    // Analytics (legacy for /api/goals/analytics). Provide simple stub lines.
    override fun getAnalytics(userId: String, range: String): AnalyticsDto {
        val lines = when (range) {
            "30d" -> listOf("Плато 2 недели подряд", "Кардио: 3 из 4 недель с улучшением")
            else -> listOf("Дефицит держится 5 дней", "Сон лучше цели 3 дня подряд")
        }
        return AnalyticsDto(range = if (range == "30d") "30d" else "7d", lines = lines)
    }

    private fun String.isNumericString(): Boolean = this.isNotBlank() && this.all { it.isDigit() || it == '.' || it == ',' }

    private fun parseDate(s: String): LocalDate = try {
        LocalDate.parse(s)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid date: must be ISO YYYY-MM-DD")
    }
}

@Serializable
private data class CoreStateDoc(
    val userId: String,
    val weightTarget: String? = null,
    val weightDateEpochMillis: Long? = null,
    val caloriesTarget: String? = null,
    val caloriesDateEpochMillis: Long? = null,
    val bodyFatTarget: String? = null,
    val bodyFatDateEpochMillis: Long? = null
)

@Serializable
private data class TrainingGoalDoc(
    val userId: String,
    val type: TrainingGoalType,
    val sessionsPerWeek: String? = null,
    val minutesPerWeek: String? = null,
    val exerciseName: String? = null,
    val exerciseWeight: String? = null,
    val exerciseReps: String? = null,
    val progressThisWeek: Int = 0
) {
    fun toDto() = TrainingGoalDto(
        type = type,
        sessionsPerWeek = sessionsPerWeek,
        minutesPerWeek = minutesPerWeek,
        exerciseName = exerciseName,
        exerciseWeight = exerciseWeight,
        exerciseReps = exerciseReps,
        progressThisWeek = progressThisWeek
    )
}

@Serializable
private data class RecoveryGoalsDoc(
    val userId: String,
    val sleepEnabled: Boolean = false,
    val sleepHours: String? = null,
    val restDaysEnabled: Boolean = false,
    val restDays: String? = null,
    val mobilityEnabled: Boolean = false,
    val mobilityMinutes: String? = null,
    val mindfulnessEnabled: Boolean = false,
    val mindfulnessMinutes: String? = null
) {
    fun toDto() = RecoveryGoalsDto(
        sleepEnabled, sleepHours, restDaysEnabled, restDays, mobilityEnabled, mobilityMinutes, mindfulnessEnabled, mindfulnessMinutes
    )
    companion object {
        fun fromDto(userId: String, dto: RecoveryGoalsDto) = RecoveryGoalsDoc(
            userId = userId,
            sleepEnabled = dto.sleepEnabled,
            sleepHours = dto.sleepHours,
            restDaysEnabled = dto.restDaysEnabled,
            restDays = dto.restDays,
            mobilityEnabled = dto.mobilityEnabled,
            mobilityMinutes = dto.mobilityMinutes,
            mindfulnessEnabled = dto.mindfulnessEnabled,
            mindfulnessMinutes = dto.mindfulnessMinutes
        )
    }
}

@Serializable
private data class RecoveryLogDoc(
    val userId: String,
    val dateEpochMillis: Long,
    val sleepHours: Int = 0,
    val restDay: Boolean = false,
    val mobilityMinutes: Int = 0,
    val mindfulnessMinutes: Int = 0
) {
    fun toDto() = RecoveryLogItem(
        dateEpochMillis = dateEpochMillis,
        sleepHours = sleepHours,
        restDay = restDay,
        mobilityMinutes = mobilityMinutes,
        mindfulnessMinutes = mindfulnessMinutes
    )
}

@Serializable
private data class CaloriesLogDoc(
    val userId: String,
    val dateEpochMillis: Long,
    val eatenCalories: Int
) {
    fun toDto() = CaloriesLogItem(
        dateEpochMillis = dateEpochMillis,
        eatenCalories = eatenCalories
    )
}

@Serializable
private data class MacrosGoalDoc(
    val userId: String,
    val proteinGrams: Int? = null,
    val fatGrams: Int? = null,
    val carbsGrams: Int? = null
)

@Serializable
private data class DayDoc(
    val enabled: Boolean,
    val session: String
) {
    fun toDto() = DayDto(enabled, session)
}

@Serializable
private data class TrainingPlanDoc(
    val userId: String,
    val template: String,
    val days: List<DayDoc>
) {
    fun toDto() = TrainingPlanDto(template = template, days = days.map { it.toDto() })
    companion object {
        fun fromDto(userId: String, dto: TrainingPlanDto) = TrainingPlanDoc(
            userId = userId,
            template = dto.template,
            days = dto.days.map { DayDoc(it.enabled, it.session) }
        )
    }
}

@Serializable
private data class TemplateDoc(
    val _id: String,
    val userId: String,
    val name: String,
    val days: List<DayDoc>
)
