package com.faigenbloom.spartaculous.weight

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface WeightRepository {
    fun list(userId: String): List<WeightEntryDto>
    fun add(userId: String, req: AddWeightRequest): WeightEntryDto
    fun clear(userId: String): Long
}

class InMemoryWeightRepository : WeightRepository {
    private val storage = ConcurrentHashMap<String, MutableList<WeightEntryDto>>() // userId -> list

    override fun list(userId: String): List<WeightEntryDto> =
        storage[userId]?.toList().orEmpty().sortedByDescending { it.recordedAtEpochMillis }

    override fun add(userId: String, req: AddWeightRequest): WeightEntryDto {
        val entry = WeightEntryDto(
            id = UUID.randomUUID().toString(),
            valueKg = req.valueKg,
            recordedAtEpochMillis = req.recordedAtEpochMillis ?: Instant.now().toEpochMilli()
        )
        storage.computeIfAbsent(userId) { mutableListOf() }.add(entry)
        return entry
    }

    override fun clear(userId: String): Long {
        val removed = storage.remove(userId)
        return removed?.size?.toLong() ?: 0L
    }
}
