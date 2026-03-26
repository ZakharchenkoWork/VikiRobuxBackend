package com.faigenbloom.spartaculous.training

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class MongoTrainingPlansRepository(
    private val mongo: TrainingPlansMongo
) : TrainingPlansRepository {

    private val log = LoggerFactory.getLogger(MongoTrainingPlansRepository::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val exerciseKeyRegex = Regex("^[a-z0-9_\\-]+$")

    companion object {
        private const val TEMPLATE_NAME_MIN = 1
        private const val TEMPLATE_NAME_MAX = 80
        private const val MAX_TEMPLATE_ITEMS = 100
        private const val MAX_DAY_PLAN_ITEMS = 100
        private const val EXERCISE_NAME_MAX = 120
    }

    override suspend fun listTemplates(userId: String): List<TrainingPlanTemplateDto> {
        return mongo.listTemplates(userId)
            .sortedBy { it.createdAt }
            .map { it.toDto() }
    }

    override suspend fun createTemplate(userId: String, request: CreateTrainingPlanTemplateRequest): TrainingPlanTemplateDto {
        val normalizedName = normalizeTemplateName(request.name)
        validateTemplateName(normalizedName)
        ensureTemplateNameUnique(userId, normalizedName.lowercase())
        val items = validateTemplateItems(request.items)

        val now = Instant.now().toEpochMilli()
        val doc = TrainingPlanTemplateDocument(
            userId = userId,
            name = normalizedName,
            nameLower = normalizedName.lowercase(),
            items = items,
            createdAt = now,
            updatedAt = now
        )

        mongo.insertTemplate(doc)
        log.info("Created training template ${'$'}{doc._id} for user=${'$'}userId")
        return doc.toDto()
    }

    override suspend fun updateTemplate(userId: String, templateId: String, request: UpdateTrainingPlanTemplateRequest): TrainingPlanTemplateDto {
        val existing = mongo.findTemplate(userId, templateId) ?: throw NotFoundException("Template not found")

        val normalizedName = normalizeTemplateName(request.name)
        validateTemplateName(normalizedName)
        val lower = normalizedName.lowercase()
        if (lower != existing.nameLower) {
            ensureTemplateNameUnique(userId, lower)
        }

        val items = validateTemplateItems(request.items)
        val updated = existing.copy(
            name = normalizedName,
            nameLower = lower,
            items = items,
            updatedAt = Instant.now().toEpochMilli()
        )

        mongo.updateTemplate(updated)
        log.info("Updated training template ${'$'}templateId for user=${'$'}userId")
        return updated.toDto()
    }

    override suspend fun deleteTemplate(userId: String, templateId: String) {
        val existing = mongo.findTemplate(userId, templateId) ?: throw NotFoundException("Template not found")
        mongo.deleteTemplate(userId, templateId)
        log.info("Deleted training template ${'$'}templateId for user=${'$'}userId")
    }

    override suspend fun applyTemplate(userId: String, templateId: String, date: String, replaceExisting: Boolean): DayPlanDto {
        val template = mongo.findTemplate(userId, templateId) ?: throw NotFoundException("Template not found")
        val normalizedDate = normalizeDate(date)

        val existingPlan = mongo.findDayPlan(userId, normalizedDate)
        if (existingPlan != null && !replaceExisting) {
            throw ConflictException("Plan for date ${'$'}normalizedDate already exists")
        }

        val items = template.items.sortedBy { it.order }.map {
            DayPlanExerciseDocument(
                exerciseKey = it.exerciseKey,
                name = it.name,
                order = it.order
            )
        }

        val doc = TrainingDayPlanDocument(
            userId = userId,
            date = normalizedDate,
            items = items,
            sourceTemplateId = template._id,
            updatedAt = Instant.now().toEpochMilli()
        )

        mongo.upsertDayPlan(doc)
        log.info("Applied template ${'$'}templateId to ${'$'}normalizedDate for user=${'$'}userId")
        return mongo.findDayPlan(userId, normalizedDate)?.toDto()
            ?: throw IllegalStateException("Failed to load plan after apply")
    }

    override suspend fun getDayPlan(userId: String, date: String): DayPlanDto {
        val normalizedDate = normalizeDate(date)
        return mongo.findDayPlan(userId, normalizedDate)?.toDto()
            ?: throw NotFoundException("Plan for date ${'$'}normalizedDate not found")
    }

    override suspend fun getDayPlansForMonth(userId: String, year: Int, month: Int): List<DayPlanDto> {
        val yearMonth = normalizeYearMonth(year, month)
        val fromDateInclusive = yearMonth.atDay(1).format(dateFormatter)
        val toDateExclusive = yearMonth.plusMonths(1).atDay(1).format(dateFormatter)

        return mongo.listDayPlansInRange(userId, fromDateInclusive, toDateExclusive)
            .sortedBy { it.date }
            .map { it.toDto() }
    }

    override suspend fun putDayPlan(userId: String, date: String, request: UpdateDayPlanRequest): DayPlanDto {
        val normalizedDate = normalizeDate(date)
        val items = validateDayPlanItems(request.items)
        val doc = TrainingDayPlanDocument(
            userId = userId,
            date = normalizedDate,
            items = items,
            sourceTemplateId = request.sourceTemplateId?.takeIf { it.isNotBlank() },
            updatedAt = Instant.now().toEpochMilli()
        )

        if (items.isEmpty()) {
            mongo.deleteDayPlan(userId, normalizedDate)
            log.info("Deleted empty day plan for ${'$'}normalizedDate user=${'$'}userId")
        } else {
            mongo.upsertDayPlan(doc)
            log.info("Saved day plan for ${'$'}normalizedDate user=${'$'}userId items=${'$'}{items.size}")
        }

        return mongo.findDayPlan(userId, normalizedDate)?.toDto()
            ?: run {
                if (items.isEmpty()) DayPlanDto(
                    date = normalizedDate,
                    items = emptyList(),
                    sourceTemplateId = null,
                    updatedAt = Instant.now().toString()
                ) else throw IllegalStateException("Failed to load plan after save")
            }
    }

    private fun normalizeTemplateName(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")

    private fun validateTemplateName(name: String) {
        if (name.length < TEMPLATE_NAME_MIN) {
            throw ValidationException("Template name is required", mapOf("name" to "Template name must be at least ${'$'}TEMPLATE_NAME_MIN character"))
        }
        if (name.length > TEMPLATE_NAME_MAX) {
            throw ValidationException("Template name is too long", mapOf("name" to "Template name must be at most ${'$'}TEMPLATE_NAME_MAX characters"))
        }
    }

    private suspend fun ensureTemplateNameUnique(userId: String, nameLower: String) {
        if (mongo.findTemplateByNameLower(userId, nameLower) != null) {
            throw ConflictException("Template name already exists")
        }
    }

    private fun validateTemplateItems(items: List<TrainingPlanTemplateItemDto>): List<TrainingPlanTemplateItemDocument> {
        if (items.isEmpty()) {
            throw ValidationException("Template must contain at least one exercise", mapOf("items" to "Add at least one exercise"))
        }
        if (items.size > MAX_TEMPLATE_ITEMS) {
            throw ValidationException("Template has too many exercises", mapOf("items" to "Maximum is ${'$'}MAX_TEMPLATE_ITEMS"))
        }

        val orders = items.map { it.order }
        if (orders.toSet().size != orders.size) {
            throw ValidationException("Exercise order values must be unique", mapOf("items" to "Duplicate order value"))
        }
        if (orders.minOrNull() != 0 || orders.maxOrNull() != items.size - 1) {
            throw ValidationException("Order must start from 0 and be continuous", mapOf("items" to "Use sequential order starting at 0"))
        }

        return items.sortedBy { it.order }.mapIndexed { index, item ->
            val key = item.exerciseKey.trim().lowercase()
            if (!exerciseKeyRegex.matches(key)) {
                throw ValidationException("Invalid exercise key", mapOf("items" to "exerciseKey '${'$'}{item.exerciseKey}' is invalid"))
            }
            val name = item.name.trim()
            if (name.isEmpty()) {
                throw ValidationException("Exercise name cannot be blank", mapOf("items" to "Exercise name is required"))
            }
            if (name.length > EXERCISE_NAME_MAX) {
                throw ValidationException("Exercise name is too long", mapOf("items" to "Exercise name must be <= ${'$'}EXERCISE_NAME_MAX characters"))
            }
            TrainingPlanTemplateItemDocument(
                exerciseKey = key,
                name = name,
                order = index
            )
        }
    }

    private fun validateDayPlanItems(items: List<DayPlanExerciseDto>): List<DayPlanExerciseDocument> {
        if (items.isEmpty()) return emptyList()
        if (items.size > MAX_DAY_PLAN_ITEMS) {
            throw ValidationException("Too many exercises in day plan", mapOf("items" to "Maximum is ${'$'}MAX_DAY_PLAN_ITEMS"))
        }

        val orders = items.map { it.order }
        if (orders.toSet().size != orders.size) {
            throw ValidationException("Exercise order values must be unique", mapOf("items" to "Duplicate order value"))
        }
        if (orders.minOrNull() != 0 || orders.maxOrNull() != items.size - 1) {
            throw ValidationException("Order must start from 0 and be continuous", mapOf("items" to "Use sequential order starting at 0"))
        }

        return items.sortedBy { it.order }.mapIndexed { index, item ->
            val key = item.exerciseKey.trim().lowercase()
            if (!exerciseKeyRegex.matches(key)) {
                throw ValidationException("Invalid exercise key", mapOf("items" to "exerciseKey '${'$'}{item.exerciseKey}' is invalid"))
            }
            val name = item.name.trim()
            if (name.isEmpty()) {
                throw ValidationException("Exercise name cannot be blank", mapOf("items" to "Exercise name is required"))
            }
            if (name.length > EXERCISE_NAME_MAX) {
                throw ValidationException("Exercise name is too long", mapOf("items" to "Exercise name must be <= ${'$'}EXERCISE_NAME_MAX characters"))
            }
            DayPlanExerciseDocument(
                exerciseKey = key,
                name = name,
                order = index
            )
        }
    }

    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        return try {
            val parsed = LocalDate.parse(trimmed, dateFormatter)
            parsed.format(dateFormatter)
        } catch (e: DateTimeParseException) {
            throw ValidationException("Invalid date format", mapOf("date" to "Expected YYYY-MM-DD"))
        }
    }

    private fun normalizeYearMonth(year: Int, month: Int): YearMonth {
        return try {
            YearMonth.of(year, month)
        } catch (_: Throwable) {
            throw ValidationException(
                "Invalid year or month",
                mapOf(
                    "year" to "Expected valid year",
                    "month" to "Expected month in range 1..12"
                )
            )
        }
    }

    private fun TrainingPlanTemplateDocument.toDto(): TrainingPlanTemplateDto = TrainingPlanTemplateDto(
        id = _id,
        name = name,
        items = items.sortedBy { it.order }.map { it.toDto() },
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
        updatedAt = Instant.ofEpochMilli(updatedAt).toString()
    )

    private fun TrainingPlanTemplateItemDocument.toDto(): TrainingPlanTemplateItemDto = TrainingPlanTemplateItemDto(
        exerciseKey = exerciseKey,
        name = name,
        order = order
    )

    private fun TrainingDayPlanDocument.toDto(): DayPlanDto = DayPlanDto(
        date = date,
        items = items.sortedBy { it.order }.map { it.toDto() },
        sourceTemplateId = sourceTemplateId,
        updatedAt = Instant.ofEpochMilli(updatedAt).toString()
    )

    private fun DayPlanExerciseDocument.toDto(): DayPlanExerciseDto = DayPlanExerciseDto(
        exerciseKey = exerciseKey,
        name = name,
        order = order
    )
}
