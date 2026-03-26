package com.faigenbloom.spartaculous.measurements

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.util.UUID

@Serializable
internal data class MeasurementEntryDoc(
    val _id: String,
    val userId: String,
    val type: MeasurementType,
    val valueCm: Float,
    // Начало суток в локальном TZ клиента (как прислал клиент)
    val dayStartEpochMillis: Long
) {
    fun toDto(): MeasurementEntryDto = MeasurementEntryDto(
        id = _id,
        type = type,
        valueCm = valueCm,
        dateEpochMillis = dayStartEpochMillis
    )
}

@Serializable
internal data class MeasurementsGoalDoc(
    val userId: String,
    val type: MeasurementType,
    val targetValueCm: Float,
    val deadlineEpochMillis: Long? = null,
    val enabled: Boolean = true,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun toNewDto(): MeasurementGoal = MeasurementGoal(
        type = type,
        targetValueCm = targetValueCm,
        deadlineEpochMillis = deadlineEpochMillis,
        enabled = enabled,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    fun toLegacyDto(): MeasurementsGoalDto = MeasurementsGoalDto(
        enabled = enabled,
        type = type,
        targetValueCm = targetValueCm
    )
}

class MeasurementsMongoRepository(db: CoroutineDatabase) : MeasurementsRepository {
    private val col: CoroutineCollection<MeasurementEntryDoc> = db.getCollection("measurements")
    private val goals: CoroutineCollection<MeasurementsGoalDoc> = db.getCollection("measurements_goals")

    init {
        // Уникальность на пользователя, тип и сутки (для измерений)
        runCatching {
            kotlinx.coroutines.runBlocking {
                col.createIndex(
                    Indexes.ascending("userId", "type", "dayStartEpochMillis"),
                    IndexOptions().unique(true)
                )
            }
        }

        // Уникальность целей по (userId, type)
        runCatching {
            kotlinx.coroutines.runBlocking {
                goals.createIndex(
                    Indexes.ascending("userId", "type"),
                    IndexOptions().unique(true)
                )
            }
        }
    }

    override suspend fun list(userId: String, fromMs: Long?, toMs: Long?): List<MeasurementEntryDto> {
        val filters = mutableListOf(Filters.eq("userId", userId))
        fromMs?.let { filters += Filters.gte("dayStartEpochMillis", it) }
        toMs?.let { filters += Filters.lte("dayStartEpochMillis", it) }
        val f = if (filters.size == 1) filters.first() else Filters.and(filters)
        return col.find(f).toList().sortedBy { it.dayStartEpochMillis }.map { it.toDto() }
    }

    override suspend fun upsert(userId: String, req: MeasurementUpsertDto): MeasurementEntryDto {
        val day = req.dateEpochMillis
        val existing = col.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", req.type),
                Filters.eq("dayStartEpochMillis", day)
            )
        ).limit(1).toList().firstOrNull()
        return if (existing != null) {
            val updated = existing.copy(valueCm = req.valueCm)
            col.replaceOne(Filters.eq("_id", existing._id), updated)
            updated.toDto()
        } else {
            val doc = MeasurementEntryDoc(
                _id = UUID.randomUUID().toString(),
                userId = userId,
                type = req.type,
                valueCm = req.valueCm,
                dayStartEpochMillis = day
            )
            col.insertOne(doc)
            doc.toDto()
        }
    }

    override suspend fun delete(userId: String, id: String): Boolean {
        return col.deleteOne(Filters.and(Filters.eq("_id", id), Filters.eq("userId", userId))).deletedCount > 0
    }

    override suspend fun listGoals(userId: String): List<MeasurementGoal> =
        goals.find(Filters.eq("userId", userId)).toList().sortedBy { it.type.name }.map { it.toNewDto() }

    override suspend fun upsertGoal(userId: String, goal: MeasurementGoalUpsertDto): MeasurementGoal {
        val existing = goals.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", goal.type)
            )
        ).limit(1).toList().firstOrNull()

        val toSave = if (existing != null) existing.copy(
            targetValueCm = goal.targetValueCm,
            deadlineEpochMillis = goal.deadlineEpochMillis,
            enabled = goal.enabled ?: existing.enabled,
            updatedAtEpochMillis = System.currentTimeMillis()
        ) else MeasurementsGoalDoc(
            userId = userId,
            type = goal.type,
            targetValueCm = goal.targetValueCm,
            deadlineEpochMillis = goal.deadlineEpochMillis,
            enabled = goal.enabled ?: true,
            updatedAtEpochMillis = System.currentTimeMillis()
        )

        if (existing == null) goals.insertOne(toSave) else goals.replaceOne(
            Filters.and(Filters.eq("userId", userId), Filters.eq("type", goal.type)), toSave
        )
        return toSave.toNewDto()
    }

    override suspend fun deleteGoal(userId: String, type: MeasurementType): Boolean {
        return goals.deleteOne(
            Filters.and(Filters.eq("userId", userId), Filters.eq("type", type))
        ).deletedCount > 0
    }

    // Обратная совместимость
    override suspend fun getGoal(userId: String): MeasurementsGoalDto? {
        val latest = goals.find(Filters.eq("userId", userId)).toList().maxByOrNull { it.updatedAtEpochMillis }
        return latest?.toLegacyDto()
    }

    override suspend fun setGoal(userId: String, goal: MeasurementsGoalDto): MeasurementsGoalDto {
        val existing = goals.find(
            Filters.and(Filters.eq("userId", userId), Filters.eq("type", goal.type))
        ).limit(1).toList().firstOrNull()
        val toSave = if (existing != null) existing.copy(
            targetValueCm = goal.targetValueCm,
            enabled = goal.enabled,
            updatedAtEpochMillis = System.currentTimeMillis()
        ) else MeasurementsGoalDoc(
            userId = userId,
            type = goal.type,
            targetValueCm = goal.targetValueCm,
            deadlineEpochMillis = null,
            enabled = goal.enabled,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        if (existing == null) goals.insertOne(toSave) else goals.replaceOne(
            Filters.and(Filters.eq("userId", userId), Filters.eq("type", goal.type)), toSave
        )
        return toSave.toLegacyDto()
    }
}
