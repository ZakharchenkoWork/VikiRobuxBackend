package com.faigenbloom.spartaculous.settings

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
import com.faigenbloom.spartaculous.premium.PremiumService
import org.koin.ktor.ext.inject

fun Route.settingsRoutes(repo: SettingsRepository) {
    route("/api/settings") {
        val firebase by inject<FirebaseService>()
        val premium by inject<PremiumService>()
        
        fun isTestToken(token: String): Boolean {
            return token == "test-token" || 
                   token == "mock-jwt-token" ||
                   token.startsWith("mock-") ||
                   token.startsWith("test-")
        }
        
        suspend fun userIdOr401(call: ApplicationCall): String? {
            val headerUid = call.request.headers["X-User-Id"]
            if (!headerUid.isNullOrBlank()) return headerUid
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

        // Profile
        get("/profile") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getProfile(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/profile") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<ProfileDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                val updated = repo.updateProfile(uid, req)
                call.respond(HttpStatusCode.OK, updated)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Cycle
        get("/cycle") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getCycle(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/cycle") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<CycleSettingsDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            try {
                repo.updateCycle(uid, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }

        // Preferences
        get("/preferences") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getPreferences(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/preferences") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<PreferencesDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            repo.updatePreferences(uid, req)
            call.respond(HttpStatusCode.NoContent)
        }

        // Appearance
        get("/appearance") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getAppearance(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/appearance") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<AppearanceSettingsDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            repo.updateAppearance(uid, req)
            call.respond(HttpStatusCode.NoContent)
        }

        // Reminders
        get("/reminders") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getReminders(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/reminders") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<ReminderSettingsDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            repo.updateReminders(uid, req)
            call.respond(HttpStatusCode.NoContent)
        }

        // Tips
        get("/tips") {
            val uid = userIdOr401(call) ?: return@get
            val dto = repo.getTips(uid)
            call.respond(HttpStatusCode.OK, dto)
        }
        put("/tips") {
            val uid = userIdOr401(call) ?: return@put
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<TipsSettingsDto>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@put
            }
            repo.updateTips(uid, req)
            call.respond(HttpStatusCode.NoContent)
        }

        // Premium
        get("/premium/status") {
            val uid = userIdOr401(call) ?: return@get
            val dto = premium.getStatus(uid)
            call.respond(HttpStatusCode.OK, dto)
        }

        // About
        get("/about") {
            userIdOr401(call) ?: return@get // keep header requirement consistent
            val dto = repo.getAbout()
            call.respond(HttpStatusCode.OK, dto)
        }
    }

    // Support (separate root)
    route("/api/support") {
        val firebase by inject<FirebaseService>()
        suspend fun userIdOr401(call: ApplicationCall): String? {
            val headerUid = call.request.headers["X-User-Id"]
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
        post("/tickets") {
            val uid = userIdOr401(call) ?: return@post
            val raw = try { call.receiveText() } catch (_: Throwable) { "" }
            val req = try {
                Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<SupportTicketRequest>(raw)
            } catch (e: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_JSON", message = e.message ?: "Invalid request body")
                return@post
            }
            try {
                repo.submitSupportTicket(uid, req)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, code = "VALIDATION_ERROR", message = e.message ?: "Bad request")
            }
        }
    }
}

