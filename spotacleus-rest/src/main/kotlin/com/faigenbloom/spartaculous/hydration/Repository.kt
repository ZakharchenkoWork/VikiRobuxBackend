package com.faigenbloom.spartaculous.hydration

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

interface HydrationRepository {
    fun getForDate(userId: String, dateEpochMillis: Long): HydrationDto?
    fun putForDate(userId: String, dto: HydrationDto)
    fun getRange(userId: String, fromEpochMillis: Long, toEpochMillis: Long): List<HydrationDto>
    fun getDayEvents(userId: String, dateEpochMillis: Long): List<HydrationEventDto>
    fun addEvent(userId: String, request: CreateHydrationEventRequest): HydrationEventDto
    fun deleteEvent(userId: String, eventId: String)
    fun updateEvent(userId: String, eventId: String, request: UpdateHydrationEventRequest): HydrationEventDto
}

class InMemoryHydrationRepository : HydrationRepository {
    private val store = ConcurrentHashMap<String, MutableMap<String, HydrationEventDto>>()
    private val dayMs = 86_400_000L

    override fun getForDate(userId: String, dateEpochMillis: Long): HydrationDto? {
        val normalizedDate = normalizeDateEpochMillis(dateEpochMillis)
        val liters = round1(getDayEvents(userId, normalizedDate).sumOf { it.amountLiters })
        if (liters <= 0.0) return null
        return HydrationDto(dateEpochMillis = normalizedDate, liters = liters)
    }

    override fun putForDate(userId: String, dto: HydrationDto) {
        validateLiters(dto.liters)
        val normalizedDate = normalizeDateEpochMillis(dto.dateEpochMillis)
        val map = store.computeIfAbsent(userId) { ConcurrentHashMap() }
        val eventIdsForDay = map.values
            .filter { event -> normalizeDateEpochMillis(event.occurredAtEpochMillis) == normalizedDate }
            .map { it.id }
        eventIdsForDay.forEach { map.remove(it) }
        val roundedLiters = round1(dto.liters)
        if (roundedLiters > 0.0) {
            val now = System.currentTimeMillis()
            val event = HydrationEventDto(
                id = UUID.randomUUID().toString(),
                occurredAtEpochMillis = normalizedDate,
                amountLiters = roundedLiters,
                type = HydrationEventType.add,
                createdAt = now,
                source = HydrationEventSource.edit
            )
            map[event.id] = event
        }
    }

    override fun getRange(userId: String, fromEpochMillis: Long, toEpochMillis: Long): List<HydrationDto> {
        if (toEpochMillis < fromEpochMillis) throw IllegalArgumentException("Invalid range: to < from")
        val days = ((toEpochMillis - fromEpochMillis) / dayMs) + 1
        if (days > 31) throw IllegalArgumentException("Range too large: max 31 days")
        val map = store[userId]?.values ?: emptyList()
        return map
            .groupBy { normalizeDateEpochMillis(it.occurredAtEpochMillis) }
            .filter { (d, _) -> d in fromEpochMillis..toEpochMillis }
            .map { (d, events) -> HydrationDto(dateEpochMillis = d, liters = round1(events.sumOf { it.amountLiters })) }
            .filter { it.liters > 0.0 }
            .sortedBy { it.dateEpochMillis }
    }

    override fun getDayEvents(userId: String, dateEpochMillis: Long): List<HydrationEventDto> {
        val normalizedDate = normalizeDateEpochMillis(dateEpochMillis)
        return store[userId]?.values
            ?.filter { event -> normalizeDateEpochMillis(event.occurredAtEpochMillis) == normalizedDate }
            ?.sortedWith(compareBy<HydrationEventDto> { it.occurredAtEpochMillis }.thenBy { it.createdAt }.thenBy { it.id })
            ?: emptyList()
    }

    override fun addEvent(userId: String, request: CreateHydrationEventRequest): HydrationEventDto {
        validateOccurredAtEpochMillis(request.occurredAtEpochMillis)
        validatePositiveLiters(request.amountLiters)
        val now = System.currentTimeMillis()
        val event = HydrationEventDto(
            id = UUID.randomUUID().toString(),
            occurredAtEpochMillis = request.occurredAtEpochMillis,
            amountLiters = round1(request.amountLiters),
            type = HydrationEventType.add,
            createdAt = now,
            source = request.source
        )
        val map = store.computeIfAbsent(userId) { ConcurrentHashMap() }
        map[event.id] = event
        return event
    }

    override fun deleteEvent(userId: String, eventId: String) {
        val map = store[userId] ?: throw NoSuchElementException("Hydration event not found")
        val removed = map.remove(eventId) ?: throw NoSuchElementException("Hydration event not found")
        if (removed.type == HydrationEventType.delete) throw NoSuchElementException("Hydration event not found")
    }

    override fun updateEvent(userId: String, eventId: String, request: UpdateHydrationEventRequest): HydrationEventDto {
        validatePositiveLiters(request.amountLiters)
        request.occurredAtEpochMillis?.let { validateOccurredAtEpochMillis(it) }
        val map = store[userId] ?: throw NoSuchElementException("Hydration event not found")
        val existing = map[eventId] ?: throw NoSuchElementException("Hydration event not found")
        val updated = existing.copy(
            occurredAtEpochMillis = request.occurredAtEpochMillis ?: existing.occurredAtEpochMillis,
            amountLiters = round1(request.amountLiters),
            type = HydrationEventType.edit,
            source = request.source ?: HydrationEventSource.edit
        )
        map[eventId] = updated
        return updated
    }

    private fun validateLiters(l: Double) {
        if (l < 0.0 || l > 10.0) throw IllegalArgumentException("liters must be between 0.0 and 10.0")
        val scaled = l * 10.0
        if (abs(scaled - scaled.roundToInt()) > 1e-9) throw IllegalArgumentException("liters precision: max 1 decimal place")
    }

    private fun validatePositiveLiters(l: Double) {
        validateLiters(l)
        if (l <= 0.0) throw IllegalArgumentException("amountLiters must be > 0.0")
    }

    private fun validateOccurredAtEpochMillis(value: Long) {
        if (value <= 0L) throw IllegalArgumentException("occurredAtEpochMillis must be positive")
    }

    private fun normalizeDateEpochMillis(value: Long): Long {
        if (value < 0L) throw IllegalArgumentException("dateEpochMillis must be >= 0")
        return value - (value % dayMs)
    }

    private fun round1(l: Double): Double = (l * 10.0).roundToInt() / 10.0
}
