package com.faigenbloom.spartaculous.bodyfat

import org.slf4j.LoggerFactory
import java.time.Instant

interface BodyFatRepository {
    suspend fun addEntry(userId: String, req: CreateBodyFatEntryRequest): BodyFatEntryDto
    suspend fun listEntries(userId: String): List<BodyFatEntryDto>
    suspend fun deleteAllEntries(userId: String)
}

class MongoBodyFatRepository(
    private val mongo: BodyFatMongo
) : BodyFatRepository {

    private val log = LoggerFactory.getLogger(MongoBodyFatRepository::class.java)

    companion object {
        private const val MIN_PERCENT = 3.0f
        private const val MAX_PERCENT = 60.0f
    }

    override suspend fun addEntry(userId: String, req: CreateBodyFatEntryRequest): BodyFatEntryDto {
        validatePercentValue(req.percentValue)

        val recordedAt = req.recordedAtEpochMillis ?: Instant.now().toEpochMilli()

        val doc = BodyFatEntryDocument(
            _id = java.util.UUID.randomUUID().toString(),
            userId = userId,
            percentValue = req.percentValue,
            recordedAtEpochMillis = recordedAt
        )

        val inserted = mongo.insert(doc)
        log.info("Created body fat entry for user=$userId, percent=${req.percentValue}")

        return BodyFatEntryDto(
            id = inserted._id,
            percentValue = inserted.percentValue,
            recordedAtEpochMillis = inserted.recordedAtEpochMillis
        )
    }

    override suspend fun listEntries(userId: String): List<BodyFatEntryDto> {
        val docs = mongo.findByUserId(userId)
        return docs.map { doc ->
            BodyFatEntryDto(
                id = doc._id,
                percentValue = doc.percentValue,
                recordedAtEpochMillis = doc.recordedAtEpochMillis
            )
        }
    }

    override suspend fun deleteAllEntries(userId: String) {
        mongo.deleteAllByUserId(userId)
        log.info("Deleted all body fat entries for user=$userId")
    }

    private fun validatePercentValue(value: Float) {
        if (value < MIN_PERCENT || value > MAX_PERCENT) {
            throw IllegalArgumentException("percentValue must be between $MIN_PERCENT and $MAX_PERCENT")
        }
    }
}
