package com.faigenbloom.spartaculous.hydration

import com.faigenbloom.spartaculous.common.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.faigenbloom.spartaculous.service.FirebaseService
import org.koin.ktor.ext.inject

fun Route.hydrationRoutes(repo: HydrationRepository) {
    val firebase by inject<FirebaseService>()
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun resolveUidOr401(call: ApplicationCall): String? {
        val headerUid = call.request.headers["X-User-Id"]
        if (!headerUid.isNullOrBlank()) return headerUid
        val auth = call.request.headers[HttpHeaders.Authorization]
        if (!auth.isNullOrBlank() && auth.startsWith("Bearer ")) {
            val token = auth.removePrefix("Bearer ").trim()
            return try {
                firebase.verifyAndGetUid(token)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return null
    }

    fun unauthorizedMessage() = "Missing X-User-Id or Bearer token"

    // GET /api/hydration?dateEpochMillis=...
    get("/api/hydration") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@get
        }
        val dateMs = call.request.queryParameters["dateEpochMillis"]?.toLongOrNull()
        if (dateMs == null) {
            call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Query param 'dateEpochMillis' is required (epoch millis)")
            return@get
        }
        try {
            val dto = repo.getForDate(uid, dateMs)
            if (dto == null) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.OK, dto)
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
        }
    }

    // PUT /api/hydration
    put("/api/hydration") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@put
        }
        val body = try { call.receiveText() } catch (_: Throwable) { "" }
        val req = try {
            json.decodeFromString<HydrationDto>(body)
        } catch (e: SerializationException) {
            call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
            return@put
        }
        try {
            repo.putForDate(uid, req)
            call.respond(HttpStatusCode.NoContent)
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
        }
    }

    // GET /api/hydration/range?fromEpochMillis=...&toEpochMillis=...
    get("/api/hydration/range") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@get
        }
        val fromMs = call.request.queryParameters["fromEpochMillis"]?.toLongOrNull()
        val toMs = call.request.queryParameters["toEpochMillis"]?.toLongOrNull()
        if (fromMs == null || toMs == null) {
            call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Query params 'fromEpochMillis' and 'toEpochMillis' are required (epoch millis)")
            return@get
        }
        try {
            val list = repo.getRange(uid, fromMs, toMs)
            call.respond(HttpStatusCode.OK, list)
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
        }
    }

    get("/api/hydration/day-events") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@get
        }
        val dateMs = call.request.queryParameters["dateEpochMillis"]?.toLongOrNull()
        if (dateMs == null) {
            call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Query param 'dateEpochMillis' is required (epoch millis)")
            return@get
        }
        try {
            call.respond(HttpStatusCode.OK, repo.getDayEvents(uid, dateMs))
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
        }
    }

    post("/api/hydration/events") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@post
        }
        val body = try { call.receiveText() } catch (_: Throwable) { "" }
        val req = try {
            json.decodeFromString<CreateHydrationEventRequest>(body)
        } catch (e: SerializationException) {
            call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
            return@post
        }
        try {
            val created = repo.addEvent(uid, req)
            call.respond(HttpStatusCode.Created, created)
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
        }
    }

    delete("/api/hydration/events/{id}") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@delete
        }
        val eventId = call.parameters["id"]
        if (eventId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Path param 'id' is required")
            return@delete
        }
        try {
            repo.deleteEvent(uid, eventId)
            call.respond(HttpStatusCode.NoContent)
        } catch (e: NoSuchElementException) {
            call.respondError(HttpStatusCode.NotFound, code = "NOT_FOUND", message = e.message ?: "Not found")
        }
    }

    patch("/api/hydration/events/{id}") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = unauthorizedMessage())
            return@patch
        }
        val eventId = call.parameters["id"]
        if (eventId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Path param 'id' is required")
            return@patch
        }
        val body = try { call.receiveText() } catch (_: Throwable) { "" }
        val req = try {
            json.decodeFromString<UpdateHydrationEventRequest>(body)
        } catch (e: SerializationException) {
            call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
            return@patch
        }
        try {
            val updated = repo.updateEvent(uid, eventId, req)
            call.respond(HttpStatusCode.OK, updated)
        } catch (e: NoSuchElementException) {
            call.respondError(HttpStatusCode.NotFound, code = "NOT_FOUND", message = e.message ?: "Not found")
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
        }
    }
}

