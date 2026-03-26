package com.faigenbloom.spartaculous.routing

import com.faigenbloom.spartaculous.training.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val USE_TEMPORARY_ENTRIES = false

private val relaxedJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Route.trainingRoutes(repo: TrainingRepository, plansRepo: TrainingPlansRepository) {
    route("/api/training") {

        fun userId(call: ApplicationCall): String =
            call.request.headers["X-User-Id"] ?: "demo-user"

        // For tests: wipe user data
        delete {
            repo.clearUser(userId(call))
            call.respond(HttpStatusCode.NoContent)
        }

        get("/exercises") {
            call.respond(HttpStatusCode.OK, repo.exerciseCatalog(userId(call)))
        }

        post("/exercises") {
            val ct = call.request.headers["Content-Type"]
            val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
            try {
                val req = Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<CreateTrainingExerciseRequest>(raw)
                val created = repo.addExercise(userId(call), req)
                call.respond(HttpStatusCode.Created, created)
            } catch (e: SerializationException) {
                call.application.environment.log.error("POST /api/training/exercises deserialization failed, CT=${ct}, raw='${raw}'", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to (e.message ?: "Conflict")))
            } catch (e: SecurityException) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Forbidden")))
            }
        }

        route("/exercises/{key}") {
            put {
                val key = call.parameters.getOrFail("key")
                val ct = call.request.headers["Content-Type"]
                val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
                try {
                    val req = Json { ignoreUnknownKeys = true; isLenient = true }
                        .decodeFromString<UpdateTrainingExerciseRequest>(raw)
                    val updated = repo.updateExercise(userId(call), key, req)
                    call.respond(HttpStatusCode.OK, updated)
                } catch (e: NoSuchElementException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Not found")))
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Forbidden")))
                } catch (e: SerializationException) {
                    call.application.environment.log.error("PUT /api/training/exercises/{key} deserialization failed, CT=${ct}, raw='${raw}'", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
                }
            }

            delete {
                val key = call.parameters.getOrFail("key")
                try {
                    repo.deleteExercise(userId(call), key)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: NoSuchElementException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Not found")))
                } catch (e: SecurityException) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Forbidden")))
                }
            }
        }

        route("/entries") {

            get {
                val response = if (USE_TEMPORARY_ENTRIES) temporaryEntries() else repo.listEntries(userId(call))
                call.respond(HttpStatusCode.OK, response)
            }

            post {
                val ct = call.request.headers["Content-Type"]
                val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
                val req = try {
                    Json { ignoreUnknownKeys = true; isLenient = true }
                        .decodeFromString<CreateTrainingEntryRequest>(raw)
                } catch (e: SerializationException) {
                    call.application.environment.log.error("POST /api/training/entries deserialization failed, CT=${ct}, raw='${raw}'", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
                    return@post
                }

                if (req.exerciseKey.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid exerciseKey"))
                    return@post
                }
                if (req.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid name"))
                    return@post
                }

                try {
                    val created = repo.addEntry(userId(call), req)
                    call.respond(HttpStatusCode.Created, created)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
                }
            }

            route("/{entryId}") {

                put {
                    val entryId = call.parameters.getOrFail("entryId")
                    val ct = call.request.headers["Content-Type"]
                    val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
                    val req = try {
                        Json { ignoreUnknownKeys = true; isLenient = true }
                            .decodeFromString<UpdateTrainingEntryRequest>(raw)
                    } catch (e: SerializationException) {
                        call.application.environment.log.error("PUT /api/training/entries/{entryId} deserialization failed, CT=${ct}, raw='${raw}'", e)
                        call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
                        return@put
                    }

                    if (req.exerciseKey.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid exerciseKey"))
                        return@put
                    }
                    if (req.name.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid name"))
                        return@put
                    }

                    try {
                        val updated = repo.updateEntry(userId(call), entryId, req)
                        call.respond(HttpStatusCode.OK, updated)
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
                    }
                }

                delete {
                    val entryId = call.parameters.getOrFail("entryId")
                    repo.deleteEntry(userId(call), entryId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        // ========== Training Plans ==========

        route("/plans") {
            route("/templates") {
                get {
                    val templates = plansRepo.listTemplates(userId(call))
                    call.respond(HttpStatusCode.OK, TrainingPlanTemplateListResponse(templates))
                }

                post {
                    val raw = call.safeReceiveText()
                    val request = try {
                        relaxedJson.decodeFromString<CreateTrainingPlanTemplateRequest>(raw)
                    } catch (e: SerializationException) {
                        call.logDecodingError("POST", "/api/training/plans/templates", raw, e)
                        call.respondApiError(HttpStatusCode.BadRequest, "INVALID_BODY", e.message ?: "Invalid request body")
                        return@post
                    }

                    try {
                        val created = plansRepo.createTemplate(userId(call), request)
                        call.respond(HttpStatusCode.Created, created)
                    } catch (e: Throwable) {
                        call.respondRepositoryException(e)
                    }
                }

                route("/{templateId}") {
                    put {
                        val templateId = call.parameters.getOrFail("templateId")
                        val raw = call.safeReceiveText()
                        val request = try {
                            relaxedJson.decodeFromString<UpdateTrainingPlanTemplateRequest>(raw)
                        } catch (e: SerializationException) {
                            call.logDecodingError("PUT", "/api/training/plans/templates/${templateId}", raw, e)
                            call.respondApiError(HttpStatusCode.BadRequest, "INVALID_BODY", e.message ?: "Invalid request body")
                            return@put
                        }

                        try {
                            val updated = plansRepo.updateTemplate(userId(call), templateId, request)
                            call.respond(HttpStatusCode.OK, updated)
                        } catch (e: Throwable) {
                            call.respondRepositoryException(e)
                        }
                    }

                    delete {
                        val templateId = call.parameters.getOrFail("templateId")
                        try {
                            plansRepo.deleteTemplate(userId(call), templateId)
                            call.respond(HttpStatusCode.NoContent)
                        } catch (e: Throwable) {
                            call.respondRepositoryException(e)
                        }
                    }

                    post("/apply") {
                        val templateId = call.parameters.getOrFail("templateId")
                        val raw = call.safeReceiveText()
                        val request = try {
                            relaxedJson.decodeFromString<ApplyTemplateToDayRequest>(raw)
                        } catch (e: SerializationException) {
                            call.logDecodingError("POST", "/api/training/plans/templates/${templateId}/apply", raw, e)
                            call.respondApiError(HttpStatusCode.BadRequest, "INVALID_BODY", e.message ?: "Invalid request body")
                            return@post
                        }

                        try {
                            val plan = plansRepo.applyTemplate(userId(call), templateId, request.date, request.replaceExisting)
                            call.respond(HttpStatusCode.OK, DayPlanResponse(plan = plan))
                        } catch (e: Throwable) {
                            call.respondRepositoryException(e)
                        }
                    }
                }
            }

            route("/day/{date}") {
                get {
                    val date = call.parameters.getOrFail("date")
                    try {
                        val plan = plansRepo.getDayPlan(userId(call), date)
                        call.respond(HttpStatusCode.OK, DayPlanResponse(plan = plan))
                    } catch (e: Throwable) {
                        call.respondRepositoryException(e)
                    }
                }

                put {
                    val date = call.parameters.getOrFail("date")
                    val raw = call.safeReceiveText()
                    val request = try {
                        relaxedJson.decodeFromString<UpdateDayPlanRequest>(raw)
                    } catch (e: SerializationException) {
                        call.logDecodingError("PUT", "/api/training/plans/day/${date}", raw, e)
                        call.respondApiError(HttpStatusCode.BadRequest, "INVALID_BODY", e.message ?: "Invalid request body")
                        return@put
                    }

                    try {
                        val plan = plansRepo.putDayPlan(userId(call), date, request)
                        val response = DayPlanResponse(plan = plan.takeIf { it.items.isNotEmpty() })
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Throwable) {
                        call.respondRepositoryException(e)
                    }
                }
            }

            get("/month") {
                val year = call.request.queryParameters["year"]?.toIntOrNull()
                val month = call.request.queryParameters["month"]?.toIntOrNull()

                if (year == null || month == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        error = "BAD_REQUEST",
                        message = "Query parameters year and month are required"
                    )
                    return@get
                }

                try {
                    val plans = plansRepo.getDayPlansForMonth(userId(call), year, month)
                    call.respond(HttpStatusCode.OK, DayPlansResponse(plans = plans))
                } catch (e: Throwable) {
                    call.respondRepositoryException(e)
                }
            }
        }
    }
}

private fun temporaryEntries(): List<TrainingEntryDto> {
    val now = Instant.now().toEpochMilli()
    return List(20) { idx ->
        val recorded = now - idx * 30L * 60L * 1000L
        val sets = if (idx % 2 == 0) 3 else 1
        val details = List(sets) { setIdx ->
            val reps = 10 + ((idx + setIdx) % 5)
            val weight = if (idx % 3 == 0) 40 + ((setIdx) * 5) else 0
            val duration = if (idx % 4 == 0) 5 + setIdx * 2 else 0
            TrainingDetailDto(
                reps = reps,
                weightKg = weight,
                durationMin = duration
            )
        }
        val summary = TrainingSummaryDto(
            sets = details.size,
            reps = details.sumOf { it.reps },
            weightKg = details.sumOf { it.weightKg },
            durationMin = details.sumOf { it.durationMin }
        )
        TrainingEntryDto(
            id = "temp-entry-${idx + 1}",
            exerciseKey = if (idx % 2 == 0) "squat" else "running",
            name = if (idx % 2 == 0) "Strength Session ${idx + 1}" else "Cardio Session ${idx + 1}",
            recordedAtEpochMillis = recorded,
            details = details,
            summary = summary
        )
    }
}

private suspend fun ApplicationCall.safeReceiveText(): String = try {
    receiveText()
} catch (_: Throwable) {
    "<unavailable>"
}

private suspend fun ApplicationCall.respondRepositoryException(e: Throwable) {
    when (e) {
        is ValidationException -> respondApiError(
            status = HttpStatusCode.UnprocessableEntity,
            error = "VALIDATION_ERROR",
            message = e.message ?: "Validation failed",
            details = e.fieldErrors
        )
        is ConflictException -> respondApiError(
            status = HttpStatusCode.Conflict,
            error = "CONFLICT",
            message = e.message ?: "Conflict"
        )
        is NotFoundException -> respondApiError(
            status = HttpStatusCode.NotFound,
            error = "NOT_FOUND",
            message = e.message ?: "Not found"
        )
        is SecurityException -> respondApiError(
            status = HttpStatusCode.Forbidden,
            error = "FORBIDDEN",
            message = e.message ?: "Forbidden"
        )
        is IllegalArgumentException -> respondApiError(
            status = HttpStatusCode.BadRequest,
            error = "BAD_REQUEST",
            message = e.message ?: "Bad request"
        )
        else -> respondApiError(
            status = HttpStatusCode.InternalServerError,
            error = "INTERNAL_ERROR",
            message = e.message ?: "Internal server error"
        )
    }
}

private suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    error: String,
    message: String,
    details: Map<String, String>? = null
) {
    respond(status, ApiError(error = error, message = message, details = details))
}

private fun ApplicationCall.logDecodingError(method: String, path: String, raw: String, e: SerializationException) {
    application.environment.log.error("${method} ${path} deserialization failed, raw='${raw}'", e)
}
