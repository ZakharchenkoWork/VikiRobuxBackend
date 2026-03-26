package com.faigenbloom.spartaculous.goals

import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.config.Config
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

fun Route.goalsRoutes(repo: GoalsRepository) {
    route("/api/goals") {
        val firebase by inject<FirebaseService>()
        
        fun isTestToken(token: String): Boolean {
            return token == "test-token" || 
                   token == "mock-jwt-token" ||
                   token.startsWith("mock-") ||
                   token.startsWith("test-")
        }
        
        suspend fun userIdOr401(call: ApplicationCall): String? {
            // Prefer explicit X-User-Id for dev
            val headerUid = call.request.headers["X-User-Id"]
            if (!headerUid.isNullOrBlank()) return headerUid
            // Else try Firebase ID token from Authorization: Bearer
            val auth = call.request.headers[HttpHeaders.Authorization]
            if (!auth.isNullOrBlank() && auth.startsWith("Bearer ")) {
                val token = auth.removePrefix("Bearer ").trim()
                
                // Check for test tokens in dev mode
                if (Config.isDev && isTestToken(token)) {
                    return "test-user"
                }
                
                return try {
                    firebase.verifyAndGetUid(token)
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = e.message ?: "Invalid token")
                    null
                }
            }
            call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
            return null
        }

        get("/overview") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getOverview(uid)
            call.respond(HttpStatusCode.OK, dto)
        }

        put("/weight") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<UpdateWeightGoalRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateWeightGoal(uid, req.target, req.dateEpochMillis)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        put("/calories") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<UpdateCaloriesGoalRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateCaloriesGoal(uid, req.target, req.dateEpochMillis)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        put("/bodyfat") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<UpdateBodyFatGoalRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateBodyFatGoal(uid, req.target, req.dateEpochMillis)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Calories logs
        get("/calories/logs") {
            val uid = userIdOr401(call) ?: return@get
            val fromMs = call.request.queryParameters["fromEpochMillis"]?.toLongOrNull()
            val toMs = call.request.queryParameters["toEpochMillis"]?.toLongOrNull()
            if (fromMs == null || toMs == null) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "fromEpochMillis and toEpochMillis are required (epoch millis)")
                return@get
            }
            try {
                val res = repo.getCaloriesLogs(uid, fromMs, toMs)
                call.respond(HttpStatusCode.OK, res)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }
        put("/calories/logs/{dateEpochMillis}") {
            val uid = userIdOr401(call) ?: return@put
            val dateMs = call.parameters["dateEpochMillis"]?.toLongOrNull()
            if (dateMs == null) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "dateEpochMillis is required (epoch millis)")
                return@put
            }
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<CaloriesLogRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.putCaloriesLog(uid, dateMs, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Macros goals
        get("/macros") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getMacrosGoal(uid)
            if (dto == null) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.OK, dto)
            }
        }
        put("/macros") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<UpdateMacrosGoalRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateMacrosGoal(uid, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Training goals
        get("/training") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getTrainingGoal(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/training") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<UpdateTrainingGoalRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateTrainingGoal(uid, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Recovery goals
        get("/recovery") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getRecoveryGoals(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/recovery") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val dto = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<RecoveryGoalsDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateRecoveryGoals(uid, dto)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Recovery logs
        get("/recovery/logs") {
            val uid = userIdOr401(call) ?: return@get
            val fromMs = call.request.queryParameters["fromEpochMillis"]?.toLongOrNull()
            val toMs = call.request.queryParameters["toEpochMillis"]?.toLongOrNull()
            if (fromMs == null || toMs == null) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "fromEpochMillis and toEpochMillis are required (epoch millis)")
                return@get
            }
            try {
                val res = repo.getRecoveryLogs(uid, fromMs, toMs)
                call.respond(HttpStatusCode.OK, res)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }
        put("/recovery/logs/{dateEpochMillis}") {
            val uid = userIdOr401(call) ?: return@put
            val dateMs = call.parameters["dateEpochMillis"]?.toLongOrNull()
            if (dateMs == null) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "dateEpochMillis is required (epoch millis)")
                return@put
            }
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<RecoveryLogRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.putRecoveryLog(uid, dateMs, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Training plan
        get("/plan") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getPlan(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/plan") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val dto = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<TrainingPlanDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updatePlan(uid, dto)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Templates
        get("/plan/templates") {
            val uid = userIdOr401(call) ?: return@get
            val list = repo.listTemplates(uid)
            call.respond(HttpStatusCode.OK, list)
        }
        post("/plan/templates") {
            val uid = userIdOr401(call) ?: return@post
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<CreateTemplateRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@post
            }
            try {
                val created = repo.createTemplate(uid, req)
                call.respond(HttpStatusCode.Created, created)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }
        put("/plan/templates/{id}") {
            val uid = userIdOr401(call) ?: return@put
            val id = call.parameters["id"] ?: run {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "id is required")
                return@put
            }
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<UpdateTemplateRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateTemplate(uid, id, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: NoSuchElementException) {
                call.respondError(HttpStatusCode.NotFound, code = "NOT_FOUND", message = e.message ?: "Not found")
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }
        delete("/plan/templates/{id}") {
            val uid = userIdOr401(call) ?: return@delete
            val id = call.parameters["id"] ?: run {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "id is required")
                return@delete
            }
            try {
                repo.deleteTemplate(uid, id)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: NoSuchElementException) {
                call.respondError(HttpStatusCode.NotFound, code = "NOT_FOUND", message = e.message ?: "Not found")
            }
        }

        // Recommendations
        get("/recommendations") {
            val uid = userIdOr401(call) ?: return@get
            val list = repo.listRecommendations(uid)
            call.respond(HttpStatusCode.OK, list)
        }
        post("/recommendations/{id}/ack") {
            val uid = userIdOr401(call) ?: return@post
            val id = call.parameters["id"] ?: run {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "id is required")
                return@post
            }
            repo.ackRecommendation(uid, id)
            call.respond(HttpStatusCode.NoContent)
        }

        // Analytics
        get("/analytics") {
            val uid = userIdOr401(call) ?: return@get
            val range = call.request.queryParameters["range"] ?: "7d"
            val dto = repo.getAnalytics(uid, range)
            call.respond(HttpStatusCode.OK, dto)
        }
    }
}
