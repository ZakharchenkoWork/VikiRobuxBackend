package com.faigenbloom

import com.faigenbloom.models.Item
import com.faigenbloom.models.ItemWrapper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Wrapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class ItemRepository(private val file: File = File("items.json")) {
    private val json = Json { prettyPrint = true }
    private val items: MutableList<Item> = mutableListOf()
    private var lastUpdateDate: Long? = null

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

    fun getAll(): ItemWrapper = ItemWrapper(
        items.sortedByDescending { it.date },
        lastUpdateDate = lastUpdateDate,
    )

    fun add(item: Item) {
        items.add(item)
        lastUpdateDate = parseDateToMillis(item.date)
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


    private fun save() {
        file.writeText(json.encodeToString(ItemWrapper(
            items.sortedByDescending { it.date },
            lastUpdateDate = lastUpdateDate,
        )))
    }

    private fun parseDateToMillis(dateString: String): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val localDateTime = LocalDateTime.parse(dateString, formatter)
        return localDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
