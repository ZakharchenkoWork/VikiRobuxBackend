package com.faigenbloom.spartaculous.goals

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface GoalsRepository {
    // Overview
    fun getOverview(userId: String): GoalsOverviewDto

    // Weight/Calories/BodyFat
    fun updateWeightGoal(userId: String, target: String, dateEpochMillis: Long?)
    fun updateCaloriesGoal(userId: String, target: String, dateEpochMillis: Long?)
    fun updateBodyFatGoal(userId: String, target: String, dateEpochMillis: Long?)

    // Training goals
    fun getTrainingGoal(userId: String): TrainingGoalDto
    fun updateTrainingGoal(userId: String, req: UpdateTrainingGoalRequest)

    // Recovery goals and logs
    fun getRecoveryGoals(userId: String): RecoveryGoalsDto
    fun updateRecoveryGoals(userId: String, dto: RecoveryGoalsDto)
    fun getRecoveryLogs(userId: String, fromEpochMillis: Long, toEpochMillis: Long): RecoveryLogsResponse
    fun putRecoveryLog(userId: String, dateEpochMillis: Long, req: RecoveryLogRequest)

    // Calories logs
    fun getCaloriesLogs(userId: String, fromEpochMillis: Long, toEpochMillis: Long): CaloriesLogsResponse
    fun putCaloriesLog(userId: String, dateEpochMillis: Long, req: CaloriesLogRequest)

    // Macros goals
    fun getMacrosGoal(userId: String): MacrosGoalDto?
    fun updateMacrosGoal(userId: String, req: UpdateMacrosGoalRequest)

    // Training plan and templates
    fun getPlan(userId: String): TrainingPlanDto
    fun updatePlan(userId: String, dto: TrainingPlanDto)
    fun listTemplates(userId: String): TemplateListDto
    fun createTemplate(userId: String, req: CreateTemplateRequest): CreateTemplateResponse
    fun updateTemplate(userId: String, id: String, req: UpdateTemplateRequest)
    fun deleteTemplate(userId: String, id: String)

    // Recommendations
    fun listRecommendations(userId: String): RecommendationsResponse
    fun ackRecommendation(userId: String, id: String)

    // Analytics
    fun getAnalytics(userId: String, range: String): AnalyticsDto
}

class InMemoryGoalsRepository : GoalsRepository {
    private data class CoreState(
        var weightTarget: String? = null,
        var weightDateEpochMillis: Long? = null,
        var caloriesTarget: String? = null,
        var caloriesDateEpochMillis: Long? = null,
        var bodyFatTarget: String? = null,
        var bodyFatDateEpochMillis: Long? = null
    )

    private val core = ConcurrentHashMap<String, CoreState>()

    // Training goal per user
    private val trainingGoal = ConcurrentHashMap<String, TrainingGoalDto>()

    // Recovery goals and logs per user
    private val recoveryGoals = ConcurrentHashMap<String, RecoveryGoalsDto>()
    private val recoveryLogs = ConcurrentHashMap<String, MutableMap<Long, RecoveryLogItem>>() // userId -> (dateEpochMillis -> item)

    // Calories logs per user
    private val caloriesLogs = ConcurrentHashMap<String, MutableMap<Long, CaloriesLogItem>>() // userId -> (dateEpochMillis -> item)

    // Macros goals per user
    private val macrosGoals = ConcurrentHashMap<String, MacrosGoalDto>()

    // Training plan per user
    private data class Template(val id: String, var name: String, var days: List<DayDto>)
    private val plan = ConcurrentHashMap<String, TrainingPlanDto>()
    private val templates = ConcurrentHashMap<String, MutableMap<String, Template>>() // userId -> (id -> template)

    // Recommendations per user
    private val recommendations = ConcurrentHashMap<String, MutableList<RecommendationDto>>()

    override fun getOverview(userId: String): GoalsOverviewDto {
        val s = core[userId]
        val tg = trainingGoal[userId]
        val rec = recommendations[userId]?.map { it.text } ?: emptyList()
        val analytics = generateAnalyticsSummary(userId, "7d")
        val recoveryPercent = computeRecoveryPercent(userId)
        val todayStart = lastNDays(1).first()
        val eatenToday = caloriesLogs[userId]?.get(todayStart)?.eatenCalories ?: 0
        return GoalsOverviewDto(
            weightTarget = s?.weightTarget,
            weightDateEpochMillis = s?.weightDateEpochMillis,
            caloriesTarget = s?.caloriesTarget,
            caloriesEatenToday = eatenToday,
            bodyFatTarget = s?.bodyFatTarget,
            bodyFatDateEpochMillis = s?.bodyFatDateEpochMillis,
            trainingWeekCount = tg?.progressThisWeek ?: 0,
            recoveryPercent = recoveryPercent,
            recommendations = rec.take(2),
            analytics = analytics.lines.take(2)
        )
    }

    override fun updateWeightGoal(userId: String, target: String, dateEpochMillis: Long?) {
        if (!target.isNumericString()) throw IllegalArgumentException("Invalid target: must be numeric string")
        val s = core.computeIfAbsent(userId) { CoreState() }
        s.weightTarget = target
        s.weightDateEpochMillis = dateEpochMillis
    }

    override fun updateCaloriesGoal(userId: String, target: String, dateEpochMillis: Long?) {
        if (!target.isNumericString()) throw IllegalArgumentException("Invalid target: must be numeric string")
        val s = core.computeIfAbsent(userId) { CoreState() }
        s.caloriesTarget = target
        s.caloriesDateEpochMillis = dateEpochMillis
    }

    override fun updateBodyFatGoal(userId: String, target: String, dateEpochMillis: Long?) {
        // Validate target can be parsed as float in range 3.0 - 60.0
        val value = target.toFloatOrNull() ?: throw IllegalArgumentException("Invalid target: must be numeric string")
        if (value < 3.0f || value > 60.0f) {
            throw IllegalArgumentException("Body fat target must be between 3.0 and 60.0")
        }
        val s = core.computeIfAbsent(userId) { CoreState() }
        s.bodyFatTarget = target
        s.bodyFatDateEpochMillis = dateEpochMillis
    }

    // Training goals
    override fun getTrainingGoal(userId: String): TrainingGoalDto {
        return trainingGoal.computeIfAbsent(userId) {
            TrainingGoalDto(
                type = TrainingGoalType.SESSIONS_PER_WEEK,
                sessionsPerWeek = "3",
                progressThisWeek = 0
            )
        }
    }

    override fun updateTrainingGoal(userId: String, req: UpdateTrainingGoalRequest) {
        // Validate numeric strings depending on type
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
        val prev = trainingGoal[userId]
        val progress = prev?.progressThisWeek ?: 0
        trainingGoal[userId] = TrainingGoalDto(
            type = req.type,
            sessionsPerWeek = req.sessionsPerWeek,
            minutesPerWeek = req.minutesPerWeek,
            exerciseName = req.exerciseName,
            exerciseWeight = req.exerciseWeight,
            exerciseReps = req.exerciseReps,
            progressThisWeek = progress
        )
    }

    // Recovery goals and logs
    override fun getRecoveryGoals(userId: String): RecoveryGoalsDto {
        return recoveryGoals.computeIfAbsent(userId) { RecoveryGoalsDto() }
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
        recoveryGoals[userId] = dto
    }

    override fun getRecoveryLogs(userId: String, fromEpochMillis: Long, toEpochMillis: Long): RecoveryLogsResponse {
        if (toEpochMillis < fromEpochMillis) throw IllegalArgumentException("Invalid range: to < from")
        val logs = recoveryLogs[userId]?.values
            ?.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
            ?.sortedBy { it.dateEpochMillis } ?: emptyList()
        return RecoveryLogsResponse(items = logs)
    }

    override fun putRecoveryLog(userId: String, dateEpochMillis: Long, req: RecoveryLogRequest) {
        val map = recoveryLogs.computeIfAbsent(userId) { ConcurrentHashMap() }
        val d = req.dateEpochMillis ?: dateEpochMillis
        map[d] = RecoveryLogItem(
            dateEpochMillis = d,
            sleepHours = req.sleepHours.coerceAtLeast(0),
            restDay = req.restDay,
            mobilityMinutes = req.mobilityMinutes.coerceAtLeast(0),
            mindfulnessMinutes = req.mindfulnessMinutes.coerceAtLeast(0)
        )
    }

    override fun getCaloriesLogs(userId: String, fromEpochMillis: Long, toEpochMillis: Long): CaloriesLogsResponse {
        if (toEpochMillis < fromEpochMillis) throw IllegalArgumentException("Invalid range: to < from")
        val logs = caloriesLogs[userId]?.values
            ?.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
            ?.sortedBy { it.dateEpochMillis } ?: emptyList()
        return CaloriesLogsResponse(items = logs)
    }

    override fun putCaloriesLog(userId: String, dateEpochMillis: Long, req: CaloriesLogRequest) {
        val map = caloriesLogs.computeIfAbsent(userId) { ConcurrentHashMap() }
        val d = req.dateEpochMillis ?: dateEpochMillis
        map[d] = CaloriesLogItem(
            dateEpochMillis = d,
            eatenCalories = req.eatenCalories.coerceAtLeast(0)
        )
    }

    // Macros goals
    override fun getMacrosGoal(userId: String): MacrosGoalDto? {
        val dto = macrosGoals[userId]
        return dto?.takeIf { it.proteinGrams != null || it.fatGrams != null || it.carbsGrams != null }
    }

    override fun updateMacrosGoal(userId: String, req: UpdateMacrosGoalRequest) {
        fun sanitize(v: String?): String? {
            if (v == null) return null
            val t = v.trim()
            val iv = t.toIntOrNull() ?: throw IllegalArgumentException("Invalid value: must be integer 0..400")
            if (iv !in 0..400) throw IllegalArgumentException("Out of range: must be 0..400")
            return iv.toString()
        }
        val prev = macrosGoals[userId] ?: MacrosGoalDto()
        val next = MacrosGoalDto(
            proteinGrams = sanitize(req.proteinGrams) ?: prev.proteinGrams,
            fatGrams = sanitize(req.fatGrams) ?: prev.fatGrams,
            carbsGrams = sanitize(req.carbsGrams) ?: prev.carbsGrams
        )
        if (next.proteinGrams == null && next.fatGrams == null && next.carbsGrams == null) {
            macrosGoals.remove(userId)
        } else {
            macrosGoals[userId] = next
        }
    }

    // Training plan and templates
    override fun getPlan(userId: String): TrainingPlanDto {
        return plan.computeIfAbsent(userId) {
            TrainingPlanDto(
                template = "Push/Pull/Legs",
                days = listOf(
                    DayDto(true, "Upper"),
                    DayDto(true, "Lower"),
                    DayDto(true, "Cardio"),
                    DayDto(true, "Upper"),
                    DayDto(true, "Lower"),
                    DayDto(false, "Rest"),
                    DayDto(false, "Rest"),
                )
            )
        }
    }

    override fun updatePlan(userId: String, dto: TrainingPlanDto) {
        if (dto.days.size != 7) throw IllegalArgumentException("Plan must contain 7 days")
        plan[userId] = dto
    }

    override fun listTemplates(userId: String): TemplateListDto {
        val list = templates[userId]?.values?.map { TemplateSummaryDto(it.id, it.name) } ?: emptyList()
        return TemplateListDto(items = list)
    }

    override fun createTemplate(userId: String, req: CreateTemplateRequest): CreateTemplateResponse {
        if (req.name.isBlank()) throw IllegalArgumentException("name is required")
        if (req.days.size != 7) throw IllegalArgumentException("Template must contain 7 days")
        val id = "tpl_" + UUID.randomUUID().toString().take(8)
        val t = Template(id = id, name = req.name, days = req.days)
        val map = templates.computeIfAbsent(userId) { ConcurrentHashMap() }
        map[id] = t
        return CreateTemplateResponse(id = id, name = req.name)
    }

    override fun updateTemplate(userId: String, id: String, req: UpdateTemplateRequest) {
        val map = templates[userId] ?: throw NoSuchElementException("Template not found")
        val t = map[id] ?: throw NoSuchElementException("Template not found")
        if (req.name.isBlank()) throw IllegalArgumentException("name is required")
        if (req.days.size != 7) throw IllegalArgumentException("Template must contain 7 days")
        t.name = req.name
        t.days = req.days
    }

    override fun deleteTemplate(userId: String, id: String) {
        val map = templates[userId] ?: throw NoSuchElementException("Template not found")
        if (map.remove(id) == null) throw NoSuchElementException("Template not found")
    }

    // Recommendations
    override fun listRecommendations(userId: String): RecommendationsResponse {
        val list = recommendations.computeIfAbsent(userId) {
            mutableListOf(
                RecommendationDto("rec_1", "Увеличь белок на 10–15 г"),
                RecommendationDto("rec_2", "Добавь 10 минут ходьбы"),
                RecommendationDto("rec_3", "Сон: цель 7.5 часов")
            )
        }
        return RecommendationsResponse(items = list.toList())
    }

    override fun ackRecommendation(userId: String, id: String) {
        val list = recommendations[userId] ?: return
        list.removeIf { it.id == id }
    }

    // Analytics
    override fun getAnalytics(userId: String, range: String): AnalyticsDto {
        // Accept only 7d or 30d for now
        val r = when (range) {
            "7d", "30d" -> range
            else -> "7d"
        }
        return generateAnalyticsSummary(userId, r)
    }

    // Helpers
    private fun String.isNumericString(): Boolean = this.isNotBlank() && this.all { it.isDigit() || it == '.' || it == ',' }

    private fun computeRecoveryPercent(userId: String): Int {
        val goals = recoveryGoals[userId] ?: return 0
        val last7 = lastNDays(7)
        val logs = recoveryLogs[userId] ?: emptyMap()
        var parts = 0
        var score = 0.0
        if (goals.sleepEnabled && !goals.sleepHours.isNullOrBlank() && goals.sleepHours.isNumericString()) {
            parts++
            val target = goals.sleepHours.replace(',', '.').toDoubleOrNull() ?: 0.0
            val avg = last7.mapNotNull { logs[it]?.sleepHours?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            score += (avg / target).coerceAtMost(1.0) * 100.0
        }
        if (goals.restDaysEnabled && !goals.restDays.isNullOrBlank() && goals.restDays.isNumericString()) {
            parts++
            val target = goals.restDays.toIntOrNull() ?: 0
            val count = last7.count { logs[it]?.restDay == true }
            score += (count.toDouble() / target.coerceAtLeast(1)).coerceAtMost(1.0) * 100.0
        }
        if (goals.mobilityEnabled && !goals.mobilityMinutes.isNullOrBlank() && goals.mobilityMinutes.isNumericString()) {
            parts++
            val targetPerDay = goals.mobilityMinutes.toIntOrNull() ?: 0
            val total = last7.sumOf { logs[it]?.mobilityMinutes ?: 0 }
            val targetTotal = targetPerDay * last7.size
            score += (total.toDouble() / targetTotal.coerceAtLeast(1)).coerceAtMost(1.0) * 100.0
        }
        if (goals.mindfulnessEnabled && !goals.mindfulnessMinutes.isNullOrBlank() && goals.mindfulnessMinutes.isNumericString()) {
            parts++
            val targetPerDay = goals.mindfulnessMinutes.toIntOrNull() ?: 0
            val total = last7.sumOf { logs[it]?.mindfulnessMinutes ?: 0 }
            val targetTotal = targetPerDay * last7.size
            score += (total.toDouble() / targetTotal.coerceAtLeast(1)).coerceAtMost(1.0) * 100.0
        }
        if (parts == 0) return 0
        return score.div(parts).toInt().coerceIn(0, 100)
    }

    private fun lastNDays(n: Int): List<Long> {
        val dayMs = 86_400_000L
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return (0 until n).map { todayStart - it * dayMs }
    }

    private fun generateAnalyticsSummary(userId: String, range: String): AnalyticsDto {
        val lines = when (range) {
            "30d" -> listOf(
                "Плато 2 недели подряд",
                "Кардио: 3 из 4 недель с улучшением"
            )
            else -> listOf(
                "Дефицит держится 5 дней",
                "Сон лучше цели 3 дня подряд"
            )
        }
        return AnalyticsDto(range = range, lines = lines)
    }
}
