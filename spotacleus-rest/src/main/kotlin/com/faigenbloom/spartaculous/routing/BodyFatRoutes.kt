package com.faigenbloom.spartaculous.routing

import com.faigenbloom.spartaculous.bodyfat.BodyFatRepository
import com.faigenbloom.spartaculous.bodyfat.CreateBodyFatEntryRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Route.bodyFatRoutes(repo: BodyFatRepository) {
    route("/api/bodyfat") {

        fun userId(call: ApplicationCall): String =
            call.request.headers["X-User-Id"] ?: throw IllegalArgumentException("X-User-Id header is required")

        get {
            try {
                val entries = repo.listEntries(userId(call))
                call.respond(HttpStatusCode.OK, entries)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Unauthorized")))
            }
        }

        post {
            val ct = call.request.headers["Content-Type"]
            val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }

            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<CreateBodyFatEntryRequest>(raw)
            } catch (e: SerializationException) {
                call.application.environment.log.error("POST /api/bodyfat deserialization failed, CT=${ct}, raw='${raw}'", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request body")))
                return@post
            }

            try {
                val created = repo.addEntry(userId(call), req)
                call.respond(HttpStatusCode.Created, created)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
            }
        }

        delete {
            try {
                repo.deleteAllEntries(userId(call))
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Unauthorized")))
            }
        }
    }
}
