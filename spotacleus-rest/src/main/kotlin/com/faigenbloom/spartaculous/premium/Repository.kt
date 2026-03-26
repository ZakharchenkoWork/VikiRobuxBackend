package com.faigenbloom.spartaculous.premium

import com.faigenbloom.spartaculous.settings.PremiumStatusDto
import com.faigenbloom.spartaculous.settings.PremiumType
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import java.time.LocalDate
import java.time.format.DateTimeParseException

interface PremiumRepository {
    suspend fun getStatus(userId: String, today: LocalDate = LocalDate.now()): PremiumStatusDto
    suspend fun setPremiumActive(
        userId: String,
        untilIso: String,
        source: String? = null,       // e.g., "google_play", "app_store", "promo"
        productId: String? = null,
        platform: String? = null      // e.g., "android", "ios"
    )
    suspend fun revoke(userId: String)
}

class MongoPremiumRepository(db: CoroutineDatabase) : PremiumRepository {
    private val col: CoroutineCollection<PremiumEntitlementDoc> = db.getCollection("premium_entitlements")


    override suspend fun getStatus(userId: String, today: LocalDate): PremiumStatusDto {
        val doc = col.findOne(PremiumEntitlementDoc::userId eq userId)
        if (doc == null) return PremiumStatusDto(PremiumType.FREE, until = null)
        val until = try { LocalDate.parse(doc.untilIso) } catch (_: DateTimeParseException) {
            return PremiumStatusDto(PremiumType.FREE, until = null)
        }
        return if (!until.isBefore(today)) PremiumStatusDto(PremiumType.ACTIVE, until = doc.untilIso)
        else PremiumStatusDto(PremiumType.FREE, until = doc.untilIso)
    }

    override suspend fun setPremiumActive(userId: String, untilIso: String, source: String?, productId: String?, platform: String?) {
        // Validate date
        try { LocalDate.parse(untilIso) } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Invalid until date: must be ISO YYYY-MM-DD")
        }
        val existing = col.findOne(PremiumEntitlementDoc::userId eq userId)
        val toSave = (existing ?: PremiumEntitlementDoc(userId = userId, untilIso = untilIso)).copy(
            untilIso = untilIso,
            source = source ?: existing?.source,
            productId = productId ?: existing?.productId,
            platform = platform ?: existing?.platform,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        col.replaceOne(Filters.eq("userId", userId), toSave, ReplaceOptions().upsert(true))
    }

    override suspend fun revoke(userId: String) {
        val existing = col.findOne(PremiumEntitlementDoc::userId eq userId) ?: return
        val toSave = existing.copy(
            untilIso = LocalDate.now().minusDays(1).toString(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        col.replaceOne(Filters.eq("userId", userId), toSave, ReplaceOptions().upsert(true))
    }
}

@Serializable
data class PremiumEntitlementDoc(
    val userId: String,
    val untilIso: String,            // ISO YYYY-MM-DD
    val source: String? = null,      // e.g., google_play, app_store, promo, admin
    val productId: String? = null,
    val platform: String? = null,    // android, ios, web
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
