package com.faigenbloom.spartaculous.routing

import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.measurements.*
import com.faigenbloom.spartaculous.service.FirebaseService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.ZoneOffset

fun Route.measurementsRoutes(repo: MeasurementsRepository) {
    route("/api/measurements") {
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

        fun parseFromTo(call: ApplicationCall): Pair<Long?, Long?> {
            val fromS = call.request.queryParameters["fromEpochMillis"]?.trim()
            val toS = call.request.queryParameters["toEpochMillis"]?.trim()
            fun parseOne(s: String?): Long? = s?.toLongOrNull()
            return parseOne(fromS) to parseOne(toS)
        }

        get("") {
            val uid = userIdOr401(call) ?: return@get
            val (from, to) = parseFromTo(call)
            val items = repo.list(uid, from, to)
            call.respond(HttpStatusCode.OK, MeasurementsListResponse(items))
        }

        put("") {
            val uid = userIdOr401(call) ?: return@put
            val body = try { call.receive<MeasurementUpsertDto>() } catch (_: Throwable) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Invalid body"); return@put
            }
            val saved = repo.upsert(uid, body)
            call.respond(HttpStatusCode.OK, saved)
        }

        delete("/{id}") {
            val uid = userIdOr401(call) ?: return@delete
            val id = call.parameters["id"].orEmpty()
            if (id.isBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "id is required"); return@delete
            }
            val ok = repo.delete(uid, id)
            if (!ok) {
                call.respondError(HttpStatusCode.NotFound, code = "NOT_FOUND", message = "Measurement not found"); return@delete
            }
            call.respond(HttpStatusCode.NoContent, Unit)
        }

        // Новая модель: список целей по типам
        get("/goals") {
            val uid = userIdOr401(call) ?: return@get
            val items = repo.listGoals(uid)
            call.respond(HttpStatusCode.OK, MeasurementGoalsResponse(items))
        }

        put("/goals") {
            val uid = userIdOr401(call) ?: return@put
            val body = try { call.receive<MeasurementGoalUpsertDto>() } catch (_: Throwable) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Invalid body"); return@put
            }

            // Валидации
            val value = body.targetValueCm
            // Если значение <= 0 — трактуем как удаление цели данного типа
            if (value <= 0f) {
                repo.deleteGoal(uid, body.type)
                call.respond(HttpStatusCode.NoContent, Unit)
                return@put
            }
            if (value > 250f) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "targetValueCm must be <= 250")
                return@put
            }
            val todayStartUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            body.deadlineEpochMillis?.let { dl ->
                if (dl < todayStartUtc) {
                    call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "deadlineEpochMillis must be >= todayStartUtc")
                    return@put
                }
            }

            val saved = repo.upsertGoal(uid, body)
            call.respond(HttpStatusCode.OK, saved)
        }

        delete("/goals/{type}") {
            val uid = userIdOr401(call) ?: return@delete
            val typeStr = call.parameters["type"].orEmpty()
            if (typeStr.isBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "type is required"); return@delete
            }
            val type = when (typeStr.lowercase()) {
                "chest" -> MeasurementType.CHEST
                "waist" -> MeasurementType.WAIST
                "hips" -> MeasurementType.HIPS
                "biceps" -> MeasurementType.BICEPS
                "thigh" -> MeasurementType.THIGH
                "calf" -> MeasurementType.CALF
                "neck" -> MeasurementType.NECK
                "shoulders" -> MeasurementType.SHOULDERS
                else -> {
                    call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Unknown type"); return@delete
                }
            }
            repo.deleteGoal(uid, type)
            call.respond(HttpStatusCode.NoContent, Unit)
        }

        get("/goal") {
            val uid = userIdOr401(call) ?: return@get
            val goal = repo.getGoal(uid) ?: MeasurementsGoalDto(enabled = false, type = MeasurementType.WAIST, targetValueCm = 0f)
            call.respond(HttpStatusCode.OK, goal)
        }

        put("/goal") {
            val uid = userIdOr401(call) ?: return@put
            val goal = try { call.receive<MeasurementsGoalDto>() } catch (_: Throwable) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Invalid body"); return@put
            }
            val saved = repo.setGoal(uid, goal)
            call.respond(HttpStatusCode.OK, saved)
        }
    }
}
