package com.faigenbloom.spartaculous.premium

import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.service.FirebaseService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Route.premiumRoutes() {
    val firebase by inject<FirebaseService>()
    val premiumService by inject<PremiumService>()
    val googlePlayService by inject<GooglePlayService>()

    route("/api/premium") {
        suspend fun userIdOr401(call: ApplicationCall): String? {
            val headerUid = call.request.headers["X-User-Id"]?.trim()
            if (!headerUid.isNullOrBlank()) return headerUid
            val auth = call.request.headers[HttpHeaders.Authorization]
            if (!auth.isNullOrBlank() && auth.startsWith("Bearer ")) {
                val token = auth.removePrefix("Bearer ").trim()
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

        post("/google/verify") {
            val uid = userIdOr401(call) ?: return@post
            val req = try {
                call.receive<GooglePlayVerifyRequest>()
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@post
            }

            if (req.platform.lowercase() != "android") {
                call.respondError(HttpStatusCode.BadRequest, code = "UNSUPPORTED_PLATFORM", message = "Platform must be 'android'")
                return@post
            }
            if (req.productType.isBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "productType is required")
                return@post
            }
            if (req.productId.isBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "productId is required")
                return@post
            }
            if (req.purchaseToken.isBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "purchaseToken is required")
                return@post
            }

            val idempotencyKey = call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() }
            if (!idempotencyKey.isNullOrBlank()) {
                // Could be wired into Redis or DB later; for now, look up cache to avoid duplicates
                // Not implemented: placeholder for future idempotency handling
            }

            val pkg = req.packageName ?: System.getenv("ANDROID_PACKAGE_NAME")?.trim()
            if (pkg.isNullOrBlank()) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = "packageName is required")
                return@post
            }

            val verifyReq = req.copy(packageName = pkg)

            val details = try {
                googlePlayService.verifyPurchase(verifyReq)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "BAD_REQUEST", message = e.message ?: "Invalid request")
                return@post
            } catch (e: GooglePlayService.UpstreamException) {
                val status = when (e.code) {
                    400 -> HttpStatusCode.BadRequest
                    401, 403 -> HttpStatusCode.Unauthorized
                    404 -> HttpStatusCode.NotFound
                    410 -> HttpStatusCode.Gone
                    429 -> HttpStatusCode.TooManyRequests
                    else -> HttpStatusCode.ServiceUnavailable
                }
                call.respondError(status, code = "UPSTREAM_ERROR", message = "Google Play error: HTTP ${e.code}", details = mapOf("body" to e.body))
                return@post
            } catch (e: Exception) {
                call.respondError(HttpStatusCode.ServiceUnavailable, code = "VERIFY_FAILED", message = e.message ?: "Verification failed")
                return@post
            }

            val expiryIso = details.expiryDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)

            when (details.state) {
                GooglePlayService.GooglePurchaseState.VALID -> {
                    val untilIso = expiryIso ?: LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    premiumService.setActive(
                        userId = uid,
                        untilIso = untilIso,
                        source = "google_play",
                        productId = details.productId,
                        platform = details.platform
                    )
                    call.respond(HttpStatusCode.OK, PremiumVerifyResponse(type = "active", until = untilIso))
                }
                GooglePlayService.GooglePurchaseState.PENDING -> {
                    call.respond(HttpStatusCode.Accepted, PremiumVerifyResponse(type = "pending", until = null))
                }
                GooglePlayService.GooglePurchaseState.CANCELLED,
                GooglePlayService.GooglePurchaseState.EXPIRED,
                GooglePlayService.GooglePurchaseState.PAUSED,
                GooglePlayService.GooglePurchaseState.UNKNOWN -> {
                    premiumService.revoke(uid)
                    call.respond(HttpStatusCode.OK, PremiumVerifyResponse(type = "free", until = expiryIso))
                }
            }
        }
    }
}
