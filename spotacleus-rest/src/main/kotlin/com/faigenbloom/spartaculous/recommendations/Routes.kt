package com.faigenbloom.spartaculous.recommendations

import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.service.FirebaseService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.recommendationsRoutes(repo: RecommendationsRepository) {
    route("/api/recommendations") {
        val firebase by inject<FirebaseService>()
        suspend fun userIdOr401(call: ApplicationCall): String? {
            val headerUid = call.request.headers["X-User-Id"]
            if (!headerUid.isNullOrBlank()) return headerUid
            val auth = call.request.headers[HttpHeaders.Authorization]
            if (!auth.isNullOrBlank() && auth.startsWith("Bearer ")) {
                val token = auth.removePrefix("Bearer ").trim()
                return try { firebase.verifyAndGetUid(token) } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = e.message ?: "Invalid token"); null
                }
            }
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
            return null
        }

        get {
            val uid = userIdOr401(call) ?: return@get
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 10
            val cursor = call.request.queryParameters["cursor"]
            val res = repo.list(uid, limit, cursor)
            call.respond(HttpStatusCode.OK, res)
        }

        get("/{id}") {
            val uid = userIdOr401(call) ?: return@get
            val id = call.parameters["id"] ?: run {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "id is required"); return@get
            }
            try {
                val detail = repo.getDetail(uid, id)
                call.respond(HttpStatusCode.OK, detail)
            } catch (e: NoSuchElementException) {
                call.respondError(HttpStatusCode.NotFound, code = "NOT_FOUND", message = e.message ?: "Not found")
            }
        }
    }
}
