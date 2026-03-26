package com.faigenbloom.spartaculous.premium

import com.faigenbloom.spartaculous.settings.PremiumStatusDto
import com.faigenbloom.spartaculous.settings.PremiumType
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class PremiumService(private val repo: PremiumRepository) {
    private data class Cache(val status: PremiumStatusDto, val expiresAtMillis: Long)

    private val ttlMillis: Long = (System.getenv("PREMIUM_CACHE_TTL_SEC")?.toLongOrNull() ?: 60L) * 1000L
    private val cache = ConcurrentHashMap<String, Cache>()

    suspend fun getStatus(userId: String): PremiumStatusDto {
        val now = System.currentTimeMillis()
        val cached = cache[userId]
        if (cached != null && now < cached.expiresAtMillis) return cached.status
        val fresh = repo.getStatus(userId, LocalDate.now())
        cache[userId] = Cache(fresh, now + ttlMillis)
        return fresh
    }

    suspend fun hasPremium(userId: String): Boolean = getStatus(userId).type == PremiumType.ACTIVE

    suspend fun setActive(userId: String, untilIso: String, source: String? = null, productId: String? = null, platform: String? = null) {
        repo.setPremiumActive(userId, untilIso, source, productId, platform)
        evict(userId)
    }

    suspend fun revoke(userId: String) {
        repo.revoke(userId)
        evict(userId)
    }

    fun evict(userId: String) {
        cache.remove(userId)
    }
}
