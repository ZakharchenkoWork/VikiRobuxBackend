package com.faigenbloom.spartaculous.training

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

interface TrainingRepository {
    fun clearUser(userId: String)

    // Exercises catalog (system + user's custom)
    fun exerciseCatalog(userId: String): List<TrainingExerciseDto>
    fun addExercise(userId: String, req: CreateTrainingExerciseRequest): TrainingExerciseDto
    fun cloneSystemExercise(userId: String, systemKey: String, overrides: CreateTrainingExerciseRequest): TrainingExerciseDto
    fun updateExercise(userId: String, key: String, req: UpdateTrainingExerciseRequest): TrainingExerciseDto
    fun deleteExercise(userId: String, key: String)
    fun hasExercise(userId: String, key: String): Boolean
    fun fetchExercise(userId: String, key: String): TrainingExerciseDto?

    fun listEntries(userId: String): List<TrainingEntryDto>
    fun addEntry(userId: String, req: CreateTrainingEntryRequest): TrainingEntryDto
    fun updateEntry(userId: String, entryId: String, req: UpdateTrainingEntryRequest): TrainingEntryDto
    fun deleteEntry(userId: String, entryId: String)
}

class ValidationException(
    message: String,
    val fieldErrors: Map<String, String>? = null
) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : RuntimeException(message)

interface TrainingPlansRepository {
    suspend fun listTemplates(userId: String): List<TrainingPlanTemplateDto>
    suspend fun createTemplate(userId: String, request: CreateTrainingPlanTemplateRequest): TrainingPlanTemplateDto
    suspend fun updateTemplate(userId: String, templateId: String, request: UpdateTrainingPlanTemplateRequest): TrainingPlanTemplateDto
    suspend fun deleteTemplate(userId: String, templateId: String)
    suspend fun applyTemplate(userId: String, templateId: String, date: String, replaceExisting: Boolean): DayPlanDto
    suspend fun getDayPlan(userId: String, date: String): DayPlanDto
    suspend fun getDayPlansForMonth(userId: String, year: Int, month: Int): List<DayPlanDto>
    suspend fun putDayPlan(userId: String, date: String, request: UpdateDayPlanRequest): DayPlanDto
}

class InMemoryTrainingRepository : TrainingRepository {
    private val log = LoggerFactory.getLogger(InMemoryTrainingRepository::class.java)
    // userId -> (entryId -> entry)
    private val entries = ConcurrentHashMap<String, ConcurrentHashMap<String, TrainingEntryDto>>()
    // userId -> (exerciseKey -> exercise)
    private val customExercises = ConcurrentHashMap<String, ConcurrentHashMap<String, TrainingExerciseDto>>()
    private val overriddenSystemKeys = ConcurrentHashMap<String, MutableSet<String>>()

    private val strengthBodyweightKeys = setOf(
        "push_ups",
        "weighted_push_ups",
        "close_grip_push_ups",
        "dips",
        "tricep_dips",
        "bench_dips",
        "wide_grip_pull_ups",
        "chin_ups",
        "weighted_pull_ups",
        "muscle_up",
        "inverted_row",
        "australian_pull_up",
        "pistol_squat",
        "jump_squat",
        "handstand_push_up"
    )

    private val bodyweightNoExtraLoadKeys = setOf(
        "plank",
        "side_plank",
        "hollow_body_hold",
        "l_sit",
        "handstand",
        "planche",
        "human_flag",
        "yoga",
        "pilates",
        "leg_stretch",
        "back_stretch",
        "splits",
        "bridge",
        "dynamic_warmup",
        "joint_mobility"
    )

    private val distanceBasedKeys = setOf(
        "running",
        "walking",
        "nordic_walking",
        "cycling",
        "stationary_bike",
        "rowing_machine",
        "rowing_outdoor",
        "swimming",
        "triathlon",
        "farmers_walk",
        "kayaking",
        "mountaineering",
        "rock_climbing",
        "skiing",
        "snowboard",
        "ice_skating",
        "rollerblading"
    )

    private val hiitKeys = setOf(
        "burpees",
        "interval_run",
        "jump_rope",
        "battle_ropes",
        "mountain_climbers"
    )

    private val coreDurationOnlyKeys = setOf(
        "plank",
        "side_plank",
        "hollow_body_hold",
        "l_sit",
        "handstand",
        "bridge"
    )

    private val functionalNoWeightKeys = setOf(
        "battle_ropes",
        "box_jump"
    )

    private val modeOverrides: Map<String, ExerciseMode> = mapOf(
        "long_jump" to ExerciseMode.CardioDistance
    )

    private val metricsOverrides: Map<String, ExerciseMetricsDto> = mapOf(
        "long_jump" to ExerciseMetricsDto(
            mode = ExerciseMode.CardioDistance,
            supportsSets = false,
            supportsReps = false,
            supportsWeight = false,
            supportsExtraLoad = false,
            supportsDuration = true,
            supportsDistance = true,
            supportsLevel = false,
            supportsTempo = false,
            supportsIntervals = false,
            supportsRestTimer = false
        )
    )

    private val defaultsOverrides: Map<String, ExerciseDefaultSettingsDto> = mapOf(
        "long_jump" to ExerciseDefaultSettingsDto(
            durationStepSec = 15,
            distanceUnit = "m"
        )
    )

    private fun resolveMode(key: String, category: String): ExerciseMode {
        modeOverrides[key]?.let { return it }
        if (hiitKeys.contains(key)) return ExerciseMode.HIIT
        val normCategory = normalizedCategory(category)
        return when (normCategory) {
            "Strength" -> if (strengthBodyweightKeys.contains(key)) ExerciseMode.StrengthBodyweight else ExerciseMode.StrengthWeighted
            "Calisthenics" -> ExerciseMode.StrengthBodyweight
            "Cardio" -> if (distanceBasedKeys.contains(key)) ExerciseMode.CardioDistance else ExerciseMode.CardioTime
            "Functional" -> if (distanceBasedKeys.contains(key)) ExerciseMode.CardioDistance else ExerciseMode.Functional
            "Core" -> ExerciseMode.Core
            "Mobility" -> ExerciseMode.Mobility
            "Combat" -> ExerciseMode.Combat
            "Sports" -> ExerciseMode.Sport
            "Outdoor" -> ExerciseMode.Outdoor
            else -> ExerciseMode.Custom
        }
    }

    private fun baseMetricsForMode(mode: ExerciseMode): ExerciseMetricsDto = when (mode) {
        ExerciseMode.StrengthWeighted -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsReps = true,
            supportsWeight = true,
            supportsExtraLoad = true,
            supportsRestTimer = true
        )
        ExerciseMode.StrengthBodyweight -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsReps = true,
            supportsExtraLoad = true,
            supportsRestTimer = true
        )
        ExerciseMode.StrengthAssisted -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsReps = true,
            supportsWeight = true,
            supportsExtraLoad = true,
            supportsRestTimer = true
        )
        ExerciseMode.CardioTime -> ExerciseMetricsDto(
            mode = mode,
            supportsDuration = true,
            supportsTempo = true,
            supportsIntervals = true
        )
        ExerciseMode.CardioDistance -> ExerciseMetricsDto(
            mode = mode,
            supportsDuration = true,
            supportsDistance = true,
            supportsTempo = true
        )
        ExerciseMode.HIIT -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsDuration = true,
            supportsTempo = true,
            supportsIntervals = true,
            supportsRestTimer = true
        )
        ExerciseMode.Circuit -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsReps = true,
            supportsDuration = true,
            supportsRestTimer = true
        )
        ExerciseMode.Mobility -> ExerciseMetricsDto(
            mode = mode,
            supportsDuration = true,
            supportsLevel = true
        )
        ExerciseMode.Core -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsReps = true,
            supportsDuration = true,
            supportsRestTimer = true
        )
        ExerciseMode.Functional -> ExerciseMetricsDto(
            mode = mode,
            supportsSets = true,
            supportsReps = true,
            supportsWeight = true,
            supportsExtraLoad = true,
            supportsTempo = true,
            supportsIntervals = true,
            supportsRestTimer = true
        )
        ExerciseMode.Sport -> ExerciseMetricsDto(
            mode = mode,
            supportsDuration = true,
            supportsDistance = true,
            supportsTempo = true
        )
        ExerciseMode.Combat -> ExerciseMetricsDto(
            mode = mode,
            supportsDuration = true,
            supportsTempo = true,
            supportsIntervals = true,
            supportsRestTimer = true
        )
        ExerciseMode.Outdoor -> ExerciseMetricsDto(
            mode = mode,
            supportsDuration = true,
            supportsDistance = true,
            supportsTempo = true
        )
        ExerciseMode.Custom -> ExerciseMetricsDto(mode = mode)
    }

    private fun deriveMetricsForExercise(key: String, category: String, override: ExerciseMetricsDto?): ExerciseMetricsDto {
        override?.let { return it }
        metricsOverrides[key]?.let { return it }
        val mode = resolveMode(key, category)
        var metrics = baseMetricsForMode(mode)

        if (mode == ExerciseMode.StrengthBodyweight && bodyweightNoExtraLoadKeys.contains(key)) {
            metrics = metrics.copy(supportsExtraLoad = false)
        }
        if (mode == ExerciseMode.Core && coreDurationOnlyKeys.contains(key)) {
            metrics = metrics.copy(supportsReps = false)
        }
        if (mode == ExerciseMode.HIIT) {
            metrics = metrics.copy(supportsSets = true, supportsDuration = true, supportsTempo = true, supportsIntervals = true, supportsRestTimer = true)
        }
        if (mode == ExerciseMode.Functional && functionalNoWeightKeys.contains(key)) {
            metrics = metrics.copy(supportsWeight = false, supportsExtraLoad = false)
        }
        return metrics
    }

    private fun baseDefaultsForMode(mode: ExerciseMode): ExerciseDefaultSettingsDto? = when (mode) {
        ExerciseMode.StrengthWeighted -> ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(6, 12), weightUnit = "kg")
        ExerciseMode.StrengthBodyweight -> ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(10, 20))
        ExerciseMode.StrengthAssisted -> ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(6, 12), weightUnit = "kg")
        ExerciseMode.CardioTime -> ExerciseDefaultSettingsDto(durationStepSec = 60)
        ExerciseMode.CardioDistance -> ExerciseDefaultSettingsDto(durationStepSec = 300, distanceUnit = "km")
        ExerciseMode.HIIT -> ExerciseDefaultSettingsDto(durationStepSec = 60)
        ExerciseMode.Circuit -> ExerciseDefaultSettingsDto(durationStepSec = 90)
        ExerciseMode.Mobility -> ExerciseDefaultSettingsDto(durationStepSec = 30)
        ExerciseMode.Core -> ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(12, 25), durationStepSec = 30)
        ExerciseMode.Functional -> ExerciseDefaultSettingsDto(repRange = ExerciseRepRangeDto(6, 15), weightUnit = "kg")
        ExerciseMode.Sport -> ExerciseDefaultSettingsDto(durationStepSec = 600, distanceUnit = "km")
        ExerciseMode.Combat -> ExerciseDefaultSettingsDto(durationStepSec = 180)
        ExerciseMode.Outdoor -> ExerciseDefaultSettingsDto(durationStepSec = 600, distanceUnit = "km")
        ExerciseMode.Custom -> null
    }

    private fun deriveDefaultSettingsForExercise(key: String, category: String, override: ExerciseDefaultSettingsDto?): ExerciseDefaultSettingsDto? {
        override?.let { return it }
        defaultsOverrides[key]?.let { return it }
        val mode = resolveMode(key, category)
        var defaults = baseDefaultsForMode(mode) ?: return null
        if (mode == ExerciseMode.StrengthBodyweight && bodyweightNoExtraLoadKeys.contains(key)) {
            defaults = defaults.copy(repRange = ExerciseRepRangeDto(12, 25))
        }
        if (mode == ExerciseMode.Core && coreDurationOnlyKeys.contains(key)) {
            defaults = defaults.copy(repRange = null, durationStepSec = defaults.durationStepSec ?: 30)
        }
        if (mode == ExerciseMode.Functional && functionalNoWeightKeys.contains(key)) {
            defaults = defaults.copy(weightUnit = null)
        }
        return defaults
    }

    private val systemExercises: ConcurrentHashMap<String, TrainingExerciseDto> = ConcurrentHashMap(
        SYSTEM_EXERCISES.associate { seed ->
            val key = seed.key.value.trim().lowercase()
            val category = normalizedCategory(seed.category)
            val metrics = deriveMetricsForExercise(key, category, seed.metrics)
            val defaults = deriveDefaultSettingsForExercise(key, category, seed.defaultSettings)
            key to TrainingExerciseDto(
                key = key,
                name = seed.name.trim(),
                category = category,
                iconKey = seed.iconKey,
                source = "system",
                metrics = metrics,
                defaultSettings = defaults
            )
        }
    )

    private val allowedCategories = setOf(
        "Strength",
        "Cardio",
        "Core",
        "Functional",
        "Calisthenics",
        "Combat",
        "Mobility",
        "Sports",
        "Outdoor"
    )

    override fun clearUser(userId: String) {
        entries.remove(userId)
        customExercises.remove(userId)
        overriddenSystemKeys.remove(userId)
    }

    override fun exerciseCatalog(userId: String): List<TrainingExerciseDto> {
        val hidden = overriddenSystemKeys[userId] ?: emptySet()
        val customs = customExercises[userId]?.values ?: emptyList()
        val filteredSystem = systemExercises.filterKeys { it !in hidden }.values
        return (filteredSystem + customs).sortedBy { it.name.lowercase() }
    }

    override fun hasExercise(userId: String, key: String): Boolean =
        systemExercises.containsKey(key) || customExercises[userId]?.containsKey(key) == true

    override fun fetchExercise(userId: String, key: String): TrainingExerciseDto? =
        customExercises[userId]?.get(key)
            ?: systemExercises[key]?.takeUnless { overriddenSystemKeys[userId]?.contains(key) == true }

    override fun cloneSystemExercise(userId: String, systemKey: String, overrides: CreateTrainingExerciseRequest): TrainingExerciseDto {
        val base = systemExercises[systemKey] ?: throw NoSuchElementException("System exercise not found")
        val name = overrides.name.trim().ifBlank { base.name }
        val category = overrides.category ?: base.category
        val iconKey = overrides.iconKey ?: base.iconKey

        validateExerciseInput(name = name, category = category, iconKey = iconKey)
        val normalizedCategory = normalizedCategory(category)

        val metrics = when {
            overrides.metrics != null -> overrides.metrics
            normalizedCategory != base.category -> deriveMetricsForExercise(systemKey, normalizedCategory, null)
            else -> base.metrics
        }
        val defaults = when {
            overrides.defaultSettings != null -> overrides.defaultSettings
            normalizedCategory != base.category -> deriveDefaultSettingsForExercise(systemKey, normalizedCategory, null)
            else -> base.defaultSettings
        }

        val custom = TrainingExerciseDto(
            key = systemKey,
            name = name,
            category = normalizedCategory,
            iconKey = iconKey,
            source = "custom",
            metrics = metrics,
            defaultSettings = defaults,
            overridesSystemKey = systemKey
        )
        customExercises.computeIfAbsent(userId) { ConcurrentHashMap() }[systemKey] = custom
        overriddenSystemKeys.computeIfAbsent(userId) { mutableSetOf() }.add(systemKey)
        return custom
    }

    override fun addExercise(userId: String, req: CreateTrainingExerciseRequest): TrainingExerciseDto {
        val name = req.name.trim()
        validateExerciseInput(name = name, category = req.category, iconKey = req.iconKey)
        // Always generate key on server; ignore client-provided key
        val existingKeys = currentKeys(userId)
        val key = generateUniqueKey(name, existingKeys)

        val normalizedCategory = normalizedCategory(req.category)
        val metrics = deriveMetricsForExercise(key, normalizedCategory, req.metrics)
        val defaults = deriveDefaultSettingsForExercise(key, normalizedCategory, req.defaultSettings)
        val dto = TrainingExerciseDto(
            key = key,
            name = name,
            category = normalizedCategory,
            iconKey = req.iconKey,
            source = "custom",
            metrics = metrics,
            defaultSettings = defaults
        )
        customExercises.computeIfAbsent(userId) { ConcurrentHashMap() }[key] = dto
        return dto
    }

    override fun updateExercise(userId: String, key: String, req: UpdateTrainingExerciseRequest): TrainingExerciseDto {
        // Forbid edits on system exercises
        val systemExercise = systemExercises[key]
        if (systemExercise != null) {
            return cloneSystemExercise(userId, key, CreateTrainingExerciseRequest(
                name = req.name ?: systemExercise.name,
                category = req.category ?: systemExercise.category,
                iconKey = req.iconKey ?: systemExercise.iconKey,
                metrics = req.metrics ?: systemExercise.metrics,
                defaultSettings = req.defaultSettings ?: systemExercise.defaultSettings
            ))
        }
        val map = customExercises[userId] ?: throw NoSuchElementException("Exercise not found")
        val existing = map[key] ?: throw NoSuchElementException("Exercise not found")

        val newName = req.name?.trim() ?: existing.name
        val newCategory = req.category ?: existing.category
        val newIcon = req.iconKey ?: existing.iconKey
        validateExerciseInput(name = newName, category = newCategory, iconKey = newIcon)

        val normalizedCategory = normalizedCategory(newCategory)
        val updatedMetrics = req.metrics
            ?: if (existing.category != normalizedCategory) deriveMetricsForExercise(key, normalizedCategory, null) else existing.metrics
        val updatedDefaults = req.defaultSettings
            ?: if (existing.category != normalizedCategory) deriveDefaultSettingsForExercise(key, normalizedCategory, null) else existing.defaultSettings

        val updated = existing.copy(
            name = newName,
            category = normalizedCategory,
            iconKey = newIcon,
            metrics = updatedMetrics,
            defaultSettings = updatedDefaults
        )
        map[key] = updated
        return updated
    }

    override fun deleteExercise(userId: String, key: String) {
        val map = customExercises[userId]
        val existing = map?.get(key)
        if (existing == null && systemExercises.containsKey(key)) throw SecurityException("System exercises cannot be deleted")
        // Prevent delete if used by entries to avoid dangling references
        val used = entries[userId]?.values?.any { it.exerciseKey == key } == true
        if (used) throw IllegalStateException("Exercise is used by entries")
        if (existing != null) {
            map.remove(key)
            existing.overridesSystemKey?.let { overridesKey ->
                val remainingOverride = map.values.any { it.overridesSystemKey == overridesKey }
                if (!remainingOverride) {
                    overriddenSystemKeys[userId]?.remove(overridesKey)
                }
            }
        } else {
            throw NoSuchElementException("Exercise not found")
        }
    }

    override fun listEntries(userId: String): List<TrainingEntryDto> =
        entries[userId]?.values?.sortedByDescending { it.recordedAtEpochMillis } ?: emptyList()

    override fun addEntry(userId: String, req: CreateTrainingEntryRequest): TrainingEntryDto {
        val exercise = fetchExercise(userId, req.exerciseKey)
            ?: throw IllegalArgumentException("exerciseKey not found")
        validateDetailsForExercise(exercise, req.details)
        val id = UUID.randomUUID().toString()
        val recorded = req.recordedAtEpochMillis ?: Instant.now().toEpochMilli()
        val summary = summarize(req.details)
        val entry = TrainingEntryDto(
            id = id,
            exerciseKey = req.exerciseKey,
            name = req.name,
            recordedAtEpochMillis = recorded,
            details = req.details,
            summary = summary
        )
        entries.computeIfAbsent(userId) { ConcurrentHashMap() }[id] = entry
        return entry
    }

    override fun updateEntry(userId: String, entryId: String, req: UpdateTrainingEntryRequest): TrainingEntryDto {
        val userMap = entries[userId] ?: throw IllegalArgumentException("Entry not found")
        val existing = userMap[entryId] ?: throw IllegalArgumentException("Entry not found")

        val newExerciseKey = req.exerciseKey ?: existing.exerciseKey
        val newName = req.name ?: existing.name
        val newRecorded = req.recordedAtEpochMillis ?: existing.recordedAtEpochMillis
        val newDetails = req.details ?: existing.details

        val exercise = fetchExercise(userId, newExerciseKey)
            ?: throw IllegalArgumentException("exerciseKey not found")
        validateDetailsForExercise(exercise, newDetails)

        val updated = existing.copy(
            exerciseKey = newExerciseKey,
            name = newName,
            recordedAtEpochMillis = newRecorded,
            details = newDetails,
            summary = summarize(newDetails)
        )
        userMap[entryId] = updated
        return updated
    }

    override fun deleteEntry(userId: String, entryId: String) {
        entries[userId]?.remove(entryId) ?: throw IllegalArgumentException("Entry not found")
    }

    private fun summarize(details: List<TrainingDetailDto>): TrainingSummaryDto {
        val sets = details.size
        val reps = details.sumOf { it.reps }
        val weightKg = details.sumOf { it.weightKg }
        val durationMin = details.sumOf { it.durationMin }
        return TrainingSummaryDto(sets = sets, reps = reps, weightKg = weightKg, durationMin = durationMin)
    }

    fun knownUserIds(): Set<String> {
        val ids = mutableSetOf<String>()
        ids += entries.keys
        ids += customExercises.keys
        ids += overriddenSystemKeys.keys
        return ids
    }

    fun seedDailyEntries(userId: String, count: Int, baseEpochMillis: Long = Instant.now().toEpochMilli()): Int {
        if (count <= 0) return 0
        val availableExercises = exerciseCatalog(userId).ifEmpty { systemExercises.values.toList() }
        if (availableExercises.isEmpty()) return 0
        var inserted = 0
        repeat(count) { idx ->
            val exercise = availableExercises[idx % availableExercises.size]
            val details = sampleDetails(exercise.metrics, idx)
            val request = CreateTrainingEntryRequest(
                exerciseKey = exercise.key,
                name = "${exercise.name} Test ${idx + 1}",
                recordedAtEpochMillis = baseEpochMillis - idx * 30L * 60L * 1000L,
                details = details
            )
            try {
                addEntry(userId, request)
                inserted++
            } catch (_: IllegalArgumentException) {
                // skip invalid entry
            }
        }
        return inserted
    }

    private fun sampleDetails(metrics: ExerciseMetricsDto, idx: Int): List<TrainingDetailDto> {
        val sets = if (metrics.supportsSets) 3 else 1
        return (0 until sets).map { setIdx ->
            val reps = if (metrics.supportsReps) 8 + (idx + setIdx) % 5 else 0
            val weight = if (metrics.supportsWeight) 20 + ((idx + setIdx) % 4) * 5 else 0
            val duration = if (metrics.supportsDuration) 5 + ((idx + setIdx) % 6) else 0
            TrainingDetailDto(
                reps = if (metrics.supportsReps) reps else 0,
                weightKg = if (metrics.supportsWeight) weight else 0,
                durationMin = if (metrics.supportsDuration) duration else 0
            )
        }
    }

    private fun validateExerciseInput(name: String, category: String, iconKey: IconKey?) {
        if (name.isBlank() || name.length > 40) throw IllegalArgumentException("Invalid name")
        val normCat = normalizedCategory(category)
        if (!allowedCategories.contains(normCat)) throw IllegalArgumentException("Invalid category")
        // iconKey is enum; invalid values fail on JSON deserialization earlier
    }

    private fun normalizedCategory(category: String): String = when (category.trim()) {
        "Strength", "strength" -> "Strength"
        "Cardio", "cardio" -> "Cardio"
        "Core", "core" -> "Core"
        "Functional", "functional" -> "Functional"
        "Calisthenics", "calisthenics" -> "Calisthenics"
        "Combat", "combat" -> "Combat"
        "Mobility", "mobility" -> "Mobility"
        "Sports", "sports" -> "Sports"
        "Outdoor", "outdoor" -> "Outdoor"
        else -> category
    }

    private fun currentKeys(userId: String): Set<String> =
        systemExercises.keys + (customExercises[userId]?.keys ?: emptySet())

    private fun generateUniqueKey(name: String, existing: Set<String>): String {
        val base = slugify(name)
        if (base !in existing) return base
        var i = 2
        while (true) {
            val k = "${base}_${i}"
            if (k !in existing) return k
            i++
        }
    }

    private fun slugify(input: String): String {
        val lower = input.lowercase()
        // replace any whitespace (\s) or hyphen '-' with underscore
        val replaced = lower.replace("[\\s-]+".toRegex(), "_")
        val cleaned = replaced.replace("[^a-z0-9_]+".toRegex(), "")
        return cleaned.trim('_')
    }

    private fun validateDetailsForExercise(exercise: TrainingExerciseDto, details: List<TrainingDetailDto>) {
        if (details.isEmpty()) throw IllegalArgumentException("Invalid details: at least one set required")
        val metrics = exercise.metrics
        if (metrics.supportsReps && details.any { it.reps <= 0 }) {
            throw IllegalArgumentException("Invalid details: reps must be > 0")
        }
        if (metrics.supportsWeight && details.any { it.weightKg <= 0 }) {
            throw IllegalArgumentException("Invalid details: weightKg must be > 0")
        }
        if (metrics.supportsDuration && details.any { it.durationMin <= 0 }) {
            throw IllegalArgumentException("Invalid details: duration must be > 0")
        }
        if (!metrics.supportsWeight && details.any { it.weightKg < 0 }) {
            throw IllegalArgumentException("Invalid details: weightKg cannot be negative")
        }
        if (!metrics.supportsReps && details.any { it.reps < 0 }) {
            throw IllegalArgumentException("Invalid details: reps cannot be negative")
        }
    }

    data class SeedReport(val inserted: Int, val updated: Int, val skipped: Int)

    fun seedSystemExercises(seeds: List<SystemExerciseSeed>): SeedReport {
        var inserted = 0
        var updated = 0
        var skipped = 0
        for (s in seeds) {
            val key = s.key.value.trim().lowercase()
            // Skip if any user has a custom with this key
            val customConflict = customExercises.values.any { it.containsKey(key) }
            if (customConflict) {
                skipped++
                log.warn("Skip seeding system exercise '{}' because a custom exercise with the same key exists", key)
                continue
            }
            val normCategory = normalizedCategory(s.category)
            if (normCategory !in allowedCategories) {
                skipped++
                log.warn("Skip seeding '{}' due to invalid category '{}'", key, s.category)
                continue
            }
            val existing = systemExercises[key]
            val metrics = deriveMetricsForExercise(key, normCategory, s.metrics)
            val defaults = deriveDefaultSettingsForExercise(key, normCategory, s.defaultSettings)
            val desired = TrainingExerciseDto(
                key = key,
                name = s.name.trim(),
                category = normCategory,
                iconKey = s.iconKey,
                source = "system",
                metrics = metrics,
                defaultSettings = defaults
            )
            if (existing == null) {
                systemExercises[key] = desired
                inserted++
            } else {
                if (existing.name != desired.name || existing.category != desired.category || existing.iconKey != desired.iconKey) {
                    systemExercises[key] = desired
                    updated++
                } else {
                    skipped++
                }
            }
        }
        log.info("System exercises seed completed: inserted={}, updated={}, skipped={}", inserted, updated, skipped)
        return SeedReport(inserted, updated, skipped)
    }
}
