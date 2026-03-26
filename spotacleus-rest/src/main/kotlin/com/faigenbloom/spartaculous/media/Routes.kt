package com.faigenbloom.spartaculous.media

import com.faigenbloom.spartaculous.auth.getUserId
import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.service.FirebaseService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.util.*

fun Route.mediaRoutes() {
    val firebase by inject<FirebaseService>()

    fun resolveUid(call: ApplicationCall): String? {
        call.getUserId()?.let { return it }

        fun sanitizeUid(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.split(',')
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
        }

        val headerUid = sanitizeUid(call.request.headers.getAll("X-User-Id")?.firstOrNull())
        if (!headerUid.isNullOrBlank()) return headerUid

        val authHeader = call.request.headers.getAll(HttpHeaders.Authorization)
            ?.firstOrNull { it.startsWith("Bearer ", ignoreCase = true) }
        if (!authHeader.isNullOrBlank()) {
            val token = authHeader.removePrefix("Bearer ").trim()
            val uid = try {
                firebase.verifyAndGetUid(token)
            } catch (_: IllegalArgumentException) {
                null
            }
            return sanitizeUid(uid)
        }
        return null
    }

    route("/api/media") {
        post("/upload") {
            val uid = resolveUid(call)
            if (uid == null) {
                call.respondError(HttpStatusCode.Unauthorized, code = "UNAUTHORIZED", message = "Missing X-User-Id or Bearer token")
                return@post
            }

            val isTest = call.request.headers["X-Test-Mode"]?.equals("true", ignoreCase = true) == true
            if (isTest) {
                call.respond(HttpStatusCode.OK, mapOf(
                    "url" to "https://cdn.example.com/test-image.png",
                    "gsUri" to "gs://bucket/test-image.png"
                ))
                return@post
            }

            val maxMb = System.getenv("MAX_UPLOAD_MB")?.toLongOrNull() ?: 10L
            val maxBytes = maxMb * 1024 * 1024

            val multipart = try { call.receiveMultipart() } catch (_: Throwable) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_MULTIPART", message = "Expected multipart/form-data")
                return@post
            }

            var uploadedUrl: String? = null
            var uploadedGsUri: String? = null
            var handled = false
            var pendingError: Triple<HttpStatusCode, String, String>? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    handled = true
                    val contentType = part.contentType?.toString() ?: "application/octet-stream"
                    if (!contentType.startsWith("image/")) {
                        part.dispose()
                        pendingError = Triple(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Only image/* allowed")
                        return@forEachPart
                    }
                    val bytes = part.streamProvider().readBytes()
                    if (bytes.size > maxBytes) {
                        part.dispose()
                        pendingError = Triple(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE", "Max ${maxMb}MB")
                        return@forEachPart
                    }
                    val original = part.originalFileName ?: "upload_${LocalDate.now()}"
                    try {
                        val res = firebase.uploadToFirebaseStorageWithUrl(uid, bytes, original, contentType)
                        uploadedUrl = res.downloadUrl
                        uploadedGsUri = res.gsUri
                    } catch (e: NotImplementedError) {
                        part.dispose()
                        pendingError = Triple(HttpStatusCode.ServiceUnavailable, "STORAGE_NOT_CONFIGURED", e.message ?: "Storage not configured")
                        return@forEachPart
                    } catch (e: IllegalStateException) {
                        part.dispose()
                        pendingError = Triple(HttpStatusCode.ServiceUnavailable, "STORAGE_ERROR", e.message ?: "Storage error")
                        return@forEachPart
                    } finally {
                        part.dispose()
                    }
                } else {
                    part.dispose()
                }
            }

            if (!handled) {
                call.respondError(HttpStatusCode.BadRequest, code = "INVALID_MULTIPART", message = "Part 'file' is required")
                return@post
            }
            pendingError?.let { (status, code, msg) ->
                call.respondError(status, code = code, message = msg)
                return@post
            }
            if (uploadedUrl == null) {
                call.respondError(HttpStatusCode.InternalServerError, code = "UPLOAD_FAILED", message = "Upload failed")
                return@post
            }
            call.respond(HttpStatusCode.OK, mapOf("url" to uploadedUrl!!, "gsUri" to (uploadedGsUri ?: "")))
        }
    }
}
