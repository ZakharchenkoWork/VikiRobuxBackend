package com.faigenbloom.spartaculous.bodyfat

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.time.Instant
import java.util.UUID

@Serializable
data class BodyFatEntryDocument(
    val _id: String,
    val userId: String,
    val percentValue: Float,
    val recordedAtEpochMillis: Long,
    val createdAt: Long = Instant.now().toEpochMilli()
)

class BodyFatMongo(private val db: CoroutineDatabase) {
    private val collection = db.getCollection<BodyFatEntryDocument>("body_fat_entries")

    suspend fun insert(entry: BodyFatEntryDocument): BodyFatEntryDocument {
        collection.insertOne(entry)
        return entry
    }

    suspend fun findByUserId(userId: String): List<BodyFatEntryDocument> {
        return collection
            .find(Filters.eq("userId", userId))
            .sort(Sorts.ascending("recordedAtEpochMillis"))
            .toList()
    }

    suspend fun deleteAllByUserId(userId: String) {
        collection.deleteMany(Filters.eq("userId", userId))
    }
}
