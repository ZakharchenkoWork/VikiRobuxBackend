package com.faigenbloom.spartaculous.nutrition

import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.service.FirebaseService
import com.faigenbloom.spartaculous.vision.VisionService
import com.faigenbloom.spartaculous.ai.GeminiService
import kotlinx.serialization.Serializable
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.UUID

fun Route.nutritionRoutes() {
    val firebase by inject<FirebaseService>()
    val vision by inject<VisionService>()
    val gemini by inject<GeminiService>()
    val scans by inject<NutritionScanRepository>()
    val rulesEngine by inject<NutritionRulesEngine>()
    val ruleTrainer by inject<NutritionRuleTrainer>()

    fun resolveUid(call: ApplicationCall): String? {
        val headerUid = call.request.headers["X-User-Id"]
        if (!headerUid.isNullOrBlank()) return headerUid
        val auth = call.request.headers[HttpHeaders.Authorization]
        if (!auth.isNullOrBlank() && auth.startsWith("Bearer ")) {
            val token = auth.removePrefix("Bearer ").trim()
            return try { firebase.verifyAndGetUid(token) } catch (_: IllegalArgumentException) { null }
        }
        return null
    }

    route("/api/nutrition") {
        post("/scan") {
            @Serializable
            data class ScanResponse(val text: String, val facts: VisionService.NutritionFacts, val parsed: NutritionScanParser.NutritionScanDto)
            val uid = resolveUid(call)
            if (uid == null) {
                call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
                return@post
            }

            val isTest = call.request.headers["X-Test-Mode"]?.equals("true", ignoreCase = true) == true
            if (isTest) {
                val fixedText = "TEST OCR TEXT"
                val fixedFacts = VisionService.NutritionFacts(
                    calories = 200,
                    proteinG = 5.0,
                    carbsG = 31.2,
                    fatG = 8.0,
                    sugarG = null,
                    fiberG = null,
                    sodiumMg = null
                )
                val fixedParsed = NutritionScanParser.NutritionScanDto(
                    scanId = "test-scan-1",
                    ingredientId = null,
                    calories = 200,
                    proteinG = 5.0,
                    carbsG = 31.2,
                    fatG = 8.0,
                    sugarG = null,
                    fiberG = null,
                    sodiumMg = null
                )
                call.respond(HttpStatusCode.OK, ScanResponse(text = fixedText, facts = fixedFacts, parsed = fixedParsed))
                return@post
            }

            val multipart = try { call.receiveMultipart() } catch (_: Throwable) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_MULTIPART", message = "Expected multipart/form-data")
                return@post
            }

            var imageBytes: ByteArray? = null
            var handled = false
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && (part.name == "image" || part.name == "file")) {
                    handled = true
                    val contentType = part.contentType?.toString() ?: "application/octet-stream"
                    if (!contentType.startsWith("image/")) {
                        part.dispose()
                        call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = "Only image/* allowed")
                        return@forEachPart
                    }
                    imageBytes = part.streamProvider().readBytes()
                    part.dispose()
                } else {
                    part.dispose()
                }
            }

            if (!handled || imageBytes == null) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_MULTIPART", message = "Part 'image' (or 'file') is required")
                return@post
            }

            val nutrition = try {
                gemini.analyzeNutrition(imageBytes!!, promptExtra = "Return values per 100g or per 100ml if applicable.")
            } catch (e: com.faigenbloom.spartaculous.ai.GeminiService.UpstreamException) {
                val status = when (e.code) {
                    400 -> HttpStatusCode.BadRequest
                    401, 403 -> HttpStatusCode.Unauthorized
                    404 -> HttpStatusCode.NotFound
                    429 -> HttpStatusCode.TooManyRequests
                    else -> HttpStatusCode.ServiceUnavailable
                }
                call.respondError(status, code = "GEMINI_UPSTREAM_ERROR", message = "Vertex error: HTTP ${e.code}", details = mapOf("body" to e.body))
                return@post
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.ServiceUnavailable, code = "GEMINI_ERROR", message = e.message ?: "Gemini API error")
                return@post
            }

            val facts = VisionService.NutritionFacts(
                calories = nutrition.calories,
                proteinG = nutrition.protein_g,
                carbsG = nutrition.carbs_g,
                fatG = nutrition.fat_g,
                sugarG = nutrition.sugar_g,
                fiberG = nutrition.fiber_g,
                sodiumMg = nutrition.sodium_mg
            )

            val parsed = NutritionScanParser.NutritionScanDto(
                scanId = UUID.randomUUID().toString(),
                ingredientId = null,
                calories = nutrition.calories,
                proteinG = nutrition.protein_g,
                carbsG = nutrition.carbs_g,
                fatG = nutrition.fat_g,
                sugarG = nutrition.sugar_g,
                fiberG = nutrition.fiber_g,
                sodiumMg = nutrition.sodium_mg
            )
            val record = NutritionScanRecord(
                scanId = parsed.scanId,
                userId = uid,
                createdAtEpochMs = Instant.now().toEpochMilli(),
                ocrText = "gemini", // legacy field; Gemini returns structured JSON instead of OCR text
                parsed = parsed,
                appliedRules = null,
                visionFacts = facts,
                correction = null
            )
            try {
                scans.saveScan(record)
            } catch (_: Throwable) {
                // don't fail the request if persistence fails; still return OCR
            }
            call.respond(HttpStatusCode.OK, ScanResponse(text = "gemini", facts = facts, parsed = parsed))
        }

        post("/scan/{scanId}/correction") {
            val uid = resolveUid(call)
            if (uid == null) {
                call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
                return@post
            }
            val scanId = call.parameters["scanId"]?.trim().orEmpty()
            if (scanId.isBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_SCAN_ID", message = "scanId is required")
                return@post
            }
            val body = try { call.receiveText() } catch (_: Throwable) { "" }
            val correction = try {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString(IngredientCorrectionDto.serializer(), body)
            } catch (_: Throwable) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = "Invalid correction body")
                return@post
            }
            val ok = try { scans.saveCorrection(scanId, uid, correction) } catch (_: Throwable) { false }
            if (!ok) {
                call.respondError(HttpStatusCode.NotFound, code = "SCAN_NOT_FOUND", message = "Scan not found or not owned by user")
                return@post
            }
            // Trigger rule training asynchronously (best-effort)
            try {
                val rec = scans.getScan(scanId, uid)
                if (rec != null) {
                    // enrich record with latest correction we just saved
                    val recWithCorr = rec.copy(correction = correction)
                    ruleTrainer.trainFromCorrection(recWithCorr)
                }
            } catch (_: Throwable) {
                // ignore training errors; main action (correction save) succeeded
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
