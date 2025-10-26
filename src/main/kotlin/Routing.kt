package com.faigenbloom

import com.faigenbloom.models.Item
import com.faigenbloom.models.ItemWrapper
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val itemRepository: ItemRepository = ItemRepository()
    routing {
        get("/getList") {
            call.respond(itemRepository.getAll())
        }
        post("/addItem") {
            itemRepository.add(call.receive<Item>())
            call.respond(itemRepository.getAll())
        }

        get("/markDone/{id}/{state}") {
            val id = call.parameters["id"] ?: return@get call.respondText("❌ id отсутствует")
            val state = call.parameters["state"]?.toBooleanStrictOrNull() ?: false
            val updated = itemRepository.markAsDone(id, state)
            if (updated != null) {
                call.respond(updated)
            } else {
                call.respondText("❌ Элемент не найден")
            }
        }
    }
}
