package com.faigenbloom.spartaculous.training

import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.litote.kmongo.combine
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.gte
import org.litote.kmongo.lt
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import java.time.Instant
import java.util.UUID

@Serializable
data class TrainingPlanTemplateItemDocument(
    val exerciseKey: String,
    val name: String,
    val order: Int
)

@Serializable
data class TrainingPlanTemplateDocument(
    val _id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String,
    val nameLower: String,
    val items: List<TrainingPlanTemplateItemDocument>,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)

@Serializable
data class DayPlanExerciseDocument(
    val exerciseKey: String,
    val name: String,
    val order: Int
)

@Serializable
data class TrainingDayPlanDocument(
    val _id: String = UUID.randomUUID().toString(),
    val userId: String,
    val date: String,
    val items: List<DayPlanExerciseDocument>,
    val sourceTemplateId: String? = null,
    val updatedAt: Long = Instant.now().toEpochMilli()
)

class TrainingPlansMongo(private val db: CoroutineDatabase) {
    private val templates = db.getCollection<TrainingPlanTemplateDocument>("training_plan_templates")
    private val dayPlans = db.getCollection<TrainingDayPlanDocument>("training_day_plans")

    suspend fun ensureIndexes() {
        // Indexes can be created manually if required
    }

    suspend fun listTemplates(userId: String): List<TrainingPlanTemplateDocument> {
        return templates.find(TrainingPlanTemplateDocument::userId eq userId).toList()
    }

    suspend fun findTemplate(userId: String, templateId: String): TrainingPlanTemplateDocument? {
        return templates.findOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("_id", templateId)
            )
        )
    }

    suspend fun findTemplateByNameLower(userId: String, nameLower: String): TrainingPlanTemplateDocument? {
        return templates.findOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("nameLower", nameLower)
            )
        )
    }

    suspend fun insertTemplate(doc: TrainingPlanTemplateDocument) {
        templates.insertOne(doc)
    }

    suspend fun updateTemplate(doc: TrainingPlanTemplateDocument) {
        templates.updateOne(
            Filters.eq("_id", doc._id),
            combine(
                setValue(TrainingPlanTemplateDocument::name, doc.name),
                setValue(TrainingPlanTemplateDocument::nameLower, doc.nameLower),
                setValue(TrainingPlanTemplateDocument::items, doc.items),
                setValue(TrainingPlanTemplateDocument::updatedAt, doc.updatedAt)
            )
        )
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        templates.deleteOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("_id", templateId)
            )
        )
    }

    suspend fun findDayPlan(userId: String, date: String): TrainingDayPlanDocument? {
        return dayPlans.findOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("date", date)
            )
        )
    }

    suspend fun listDayPlansInRange(userId: String, fromDateInclusive: String, toDateExclusive: String): List<TrainingDayPlanDocument> {
        return dayPlans.find(
            Filters.and(
                TrainingDayPlanDocument::userId eq userId,
                TrainingDayPlanDocument::date gte fromDateInclusive,
                TrainingDayPlanDocument::date lt toDateExclusive
            )
        ).toList()
    }

    suspend fun upsertDayPlan(doc: TrainingDayPlanDocument) {
        dayPlans.deleteOne(
            Filters.and(
                Filters.eq("userId", doc.userId),
                Filters.eq("date", doc.date)
            )
        )
        dayPlans.insertOne(doc.copy(updatedAt = Instant.now().toEpochMilli()))
    }

    suspend fun deleteDayPlan(userId: String, date: String) {
        dayPlans.deleteOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("date", date)
            )
        )
    }
}
