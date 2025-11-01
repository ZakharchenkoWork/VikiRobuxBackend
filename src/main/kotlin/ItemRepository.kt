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

class ItemRepository {
    val mongo = Mongo()

    suspend fun getAll(): ItemWrapper {
        val items = mongo.getItems()

        val lastUpdateDate = items
            .maxOfOrNull { parseDateToMillis(it.date) }

        return ItemWrapper(
            items = items.sortedByDescending { parseDateToMillis(it.date) },
            lastUpdateDate = lastUpdateDate
        )
    }

    suspend fun add(item: Item) {
        mongo.addItem(item)
    }

    suspend fun markAsDone(id: String, isDone: Boolean = true){
        mongo.markAsDone(id, isDone)
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
