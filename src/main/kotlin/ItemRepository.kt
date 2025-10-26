package com.faigenbloom

import com.faigenbloom.models.Item
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class ItemRepository(private val file: File = File("items.json")) {
    private val json = Json { prettyPrint = true }
    private val items: MutableList<Item> = mutableListOf()

    init {
        if (file.exists()) {
            runCatching {
                val text = file.readText()
                if (text.isNotBlank()) {
                    items.addAll(json.decodeFromString(text))
                }
            }.onFailure { println("⚠️ Не удалось загрузить items.json: $it") }
        }
    }

    fun getAll(): List<Item> = items.sortedByDescending { it.date }

    fun add(item: Item) {
        items.add(item)
        save()
    }

    fun markAsDone(id: String, isDone: Boolean = true): Item? {
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = items[index].copy(isDone = isDone)
            items[index] = updated
            save()
            return updated
        }
        return null
    }

    fun clearAll() {
        items.clear()
        save()
    }

    private fun save() {
        file.writeText(json.encodeToString(items))
    }
}
