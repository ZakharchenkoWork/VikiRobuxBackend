package com.faigenbloom.spartaculous.analytics

import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.service.FirebaseService
import com.faigenbloom.spartaculous.measurements.MeasurementsRepository
import com.faigenbloom.spartaculous.measurements.MeasurementType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlin.math.sin

fun Route.analyticsRoutes() {
    route("/api/analytics") {
        val firebase by inject<FirebaseService>()
        val measurements by inject<MeasurementsRepository>()
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

        get("/dashboard") {
            val uid = userIdOr401(call) ?: return@get
            val fromS = call.request.queryParameters["from"]
            val toS = call.request.queryParameters["to"]
            if (fromS.isNullOrBlank() || toS.isNullOrBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "from and to are required in ISO YYYY-MM-DD")
                return@get
            }
            val from = try { LocalDate.parse(fromS) } catch (_: DateTimeParseException) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Invalid date: must be ISO YYYY-MM-DD"); return@get
            }
            val to = try { LocalDate.parse(toS) } catch (_: DateTimeParseException) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Invalid date: must be ISO YYYY-MM-DD"); return@get
            }
            if (to.isBefore(from)) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "Invalid range: to < from"); return@get
            }
            val days = (0..(to.toEpochDay() - from.toEpochDay()).toInt()).map { from.plusDays(it.toLong()) }
            // Deterministic lightweight series generator by index
            fun wave(i: Int, base: Double, amp: Double): Double = base + amp * sin(i / 3.0)

            val weight = days.mapIndexed { i, _ -> (wave(i, 82.0, 0.6) - i * 0.03).let { String.format("%.1f", it).toDouble() } }
            val calories = days.mapIndexed { i, _ -> (2100 - (i % 5) * 50).toDouble() }

            val trainingStability = days.mapIndexed { i, _ -> if ((i % 2) == 0) 1 else 0 }
            val trainingMinutes = days.mapIndexed { i, _ -> listOf(0, 20, 30, 45, 60)[i % 5] }

            val protein = days.mapIndexed { i, _ -> 120.0 + (i % 3) * 10.0 }
            val fat = days.mapIndexed { i, _ -> 60.0 + ((i + 1) % 3) * 5.0 }
            val carbs = days.mapIndexed { i, _ -> 180.0 - (i % 3) * 10.0 }

            val micros = listOf(
                MicroDto(key = "MicroIron", progress = 0.6),
                MicroDto(key = "MicroCalcium", progress = 0.4)
            )

            val water = days.mapIndexed { i, _ -> wave(i, 1.2, 0.2) }
            val sleep = days.mapIndexed { i, _ -> wave(i, 7.0, 0.8) }
            val recovery = days.mapIndexed { i, _ -> wave(i, 0.6, 0.15).coerceIn(0.0, 1.0) }

            // Build measurements series from real data
            val fromMs = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val toMs = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val goal = measurements.getGoal(uid)
            val mType = if (goal?.enabled == true) goal.type else MeasurementType.WAIST
            val measItems = measurements.list(uid, fromMs, toMs).filter { it.type == mType }
            val measByDay = measItems.associateBy { it.dateEpochMillis }
            val measValues = days.map { d ->
                val key = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                measByDay[key]?.valueCm?.toDouble() ?: kotlin.run { Double.NaN }
            }

            val dto = AnalyticsDashboardDto(
                weightCalories = listOf(
                    SeriesDto(values = weight, color = "Primary"),
                    SeriesDto(values = calories, color = "Orange")
                ),
                trainingStability = trainingStability,
                trainingMinutes = trainingMinutes,
                macros = listOf(
                    SeriesDto(values = protein, color = "Green"),
                    SeriesDto(values = fat, color = "Purple"),
                    SeriesDto(values = carbs, color = "Orange")
                ),
                micros = micros,
                water = listOf(SeriesDto(values = water, color = "Blue")),
                sleep = listOf(SeriesDto(values = sleep, color = "Violet")),
                recovery = listOf(SeriesDto(values = recovery, color = "Teal")),
                measurements = listOf(SeriesDto(values = measValues, color = "orange"))
            )

            call.respond(HttpStatusCode.OK, dto)
        }
    }
}
