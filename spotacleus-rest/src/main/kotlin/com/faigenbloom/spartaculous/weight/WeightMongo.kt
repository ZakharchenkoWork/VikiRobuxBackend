package com.faigenbloom.spartaculous.weight

import com.mongodb.client.model.Filters
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.toList
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.time.Instant
import java.util.UUID

@Serializable
internal data class WeightEntryDoc(
    val _id: String,
    val userId: String,
    val valueKg: Float,
    val recordedAtEpochMillis: Long
) {
    fun toDto(): WeightEntryDto = WeightEntryDto(
        id = _id,
        valueKg = valueKg,
        recordedAtEpochMillis = recordedAtEpochMillis
    )
}

class WeightMongo(private val db: CoroutineDatabase) {
    private val col: CoroutineCollection<WeightEntryDoc> = db.getCollection<WeightEntryDoc>("weights")

    suspend fun list(userId: String): List<WeightEntryDto> =
        col.find(Filters.eq("userId", userId)).toList().map { it.toDto() }

    suspend fun add(userId: String, req: AddWeightRequest): WeightEntryDto {
        val doc = WeightEntryDoc(
            _id = UUID.randomUUID().toString(),
            userId = userId,
            valueKg = req.valueKg,
            recordedAtEpochMillis = req.recordedAtEpochMillis ?: Instant.now().toEpochMilli()
        )
        col.insertOne(doc)
        return doc.toDto()
    }

    suspend fun clear(userId: String): Long =
        col.deleteMany(Filters.eq("userId", userId)).deletedCount
}
