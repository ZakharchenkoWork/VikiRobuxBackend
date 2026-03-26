package com.faigenbloom.spartaculous.recommendations

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

interface RecommendationsRepository {
    fun list(userId: String, limit: Int = 10, cursor: String? = null): RecommendationListResponse
    fun getDetail(userId: String, id: String): RecommendationDetailDto
}

class MongoRecommendationsRepository(db: CoroutineDatabase) : RecommendationsRepository {
    private val col: CoroutineCollection<RecommendationDoc> = db.getCollection("recommendations")

    override fun list(userId: String, limit: Int, cursor: String?): RecommendationListResponse = kotlinx.coroutines.runBlocking {
        ensureSeed(userId)
        val filter = if (cursor.isNullOrBlank()) {
            Filters.eq("userId", userId)
        } else {
            Filters.and(
                Filters.eq("userId", userId),
                Filters.lt("publishedAt", cursor)
            )
        }
        val docs = col.find(filter).sort(Sorts.descending("publishedAt")).limit(limit).toList()
        val items = docs.map { it.toListItem() }
        val next = if (docs.size == limit) docs.last().publishedAt else null
        RecommendationListResponse(items = items, nextCursor = next)
    }

    override fun getDetail(userId: String, id: String): RecommendationDetailDto = kotlinx.coroutines.runBlocking {
        val doc = col.find(Filters.and(Filters.eq("userId", userId), Filters.eq("_id", id))).limit(1).toList().firstOrNull()
            ?: throw NoSuchElementException("Recommendation not found")
        doc.toDetail()
    }

    private suspend fun ensureSeed(userId: String) {
        val any = col.find(RecommendationDoc::userId eq userId).limit(1).toList().firstOrNull()
        if (any == null) {
            val now = Instant.now()
            val fmt = DateTimeFormatter.ISO_INSTANT
            val docs = listOf(
                RecommendationDoc(
                    _id = "rec_" + UUID.randomUUID().toString().take(8),
                    userId = userId,
                    title = "Почему сон влияет на восстановление",
                    preview = "Короткий текст 2–4 строки…",
                    imageUrl = null,
                    publishedAt = fmt.format(now.atOffset(ZoneOffset.UTC)),
                    body = "Полный текст статьи про сон..."
                ),
                RecommendationDoc(
                    _id = "rec_" + UUID.randomUUID().toString().take(8),
                    userId = userId,
                    title = "Как добавить 10 минут активности",
                    preview = "Идеи для быстрой ходьбы и разминки",
                    imageUrl = null,
                    publishedAt = fmt.format(now.minusSeconds(3600).atOffset(ZoneOffset.UTC)),
                    body = "Полный текст статьи про активность..."
                )
            )
            col.insertMany(docs)
        }
    }
}

@Serializable
internal data class RecommendationDoc(
    val _id: String,
    val userId: String,
    val title: String,
    val preview: String,
    val imageUrl: String? = null,
    val publishedAt: String, // RFC3339/ISO_INSTANT
    val body: String
) {
    fun toListItem() = RecommendationListItemDto(
        id = _id,
        title = title,
        preview = preview,
        imageUrl = imageUrl,
        publishedAt = publishedAt
    )
    fun toDetail() = RecommendationDetailDto(
        id = _id,
        title = title,
        imageUrl = imageUrl,
        body = body
    )
}
