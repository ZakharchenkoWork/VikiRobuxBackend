package com.faigenbloom.spartaculous.hydration

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

interface HydrationRepository {
    fun getForDate(userId: String, dateEpochMillis: Long): HydrationDto?
    fun putForDate(userId: String, dto: HydrationDto)
    fun getRange(userId: String, fromEpochMillis: Long, toEpochMillis: Long): List<HydrationDto>
}

class InMemoryHydrationRepository : HydrationRepository {
    // userId -> (dateEpochMillis -> liters)
    private val store = ConcurrentHashMap<String, MutableMap<Long, Double>>()

    override fun getForDate(userId: String, dateEpochMillis: Long): HydrationDto? {
        val liters = store[userId]?.get(dateEpochMillis) ?: return null
        return HydrationDto(dateEpochMillis = dateEpochMillis, liters = liters)
    }

    override fun putForDate(userId: String, dto: HydrationDto) {
        validateLiters(dto.liters)
        val map = store.computeIfAbsent(userId) { ConcurrentHashMap() }
        map[dto.dateEpochMillis] = round1(dto.liters)
    }

    override fun getRange(userId: String, fromEpochMillis: Long, toEpochMillis: Long): List<HydrationDto> {
        if (toEpochMillis < fromEpochMillis) throw IllegalArgumentException("Invalid range: to < from")
        val dayMs = 86_400_000L
        val days = ((toEpochMillis - fromEpochMillis) / dayMs) + 1
        if (days > 31) throw IllegalArgumentException("Range too large: max 31 days")
        val map = store[userId] ?: emptyMap()
        return map
            .filter { (d, v) -> d in fromEpochMillis..toEpochMillis && v > 0.0 }
            .map { (d, v) -> HydrationDto(dateEpochMillis = d, liters = v) }
            .sortedBy { it.dateEpochMillis }
    }

    private fun validateLiters(l: Double) {
        if (l < 0.0 || l > 10.0) throw IllegalArgumentException("liters must be between 0.0 and 10.0")
        val scaled = l * 10.0
        if (abs(scaled - scaled.roundToInt()) > 1e-9) throw IllegalArgumentException("liters precision: max 1 decimal place")
    }

    private fun round1(l: Double): Double = (l * 10.0).roundToInt() / 10.0
}
