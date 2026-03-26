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

    // GET /api/hydration?dateEpochMillis=...
    get("/api/hydration") {
        val uid = resolveUidOr401(call)
        if (uid == null) {
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
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
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
            return@put
        }
        val body = try { call.receiveText() } catch (_: Throwable) { "" }
        val req = try {
            Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<HydrationDto>(body)
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
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
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
}

