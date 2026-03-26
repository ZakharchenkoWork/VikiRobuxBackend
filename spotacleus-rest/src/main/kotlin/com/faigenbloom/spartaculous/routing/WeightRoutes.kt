package com.faigenbloom.spartaculous.routing

import com.faigenbloom.spartaculous.weight.AddWeightRequest
import com.faigenbloom.spartaculous.weight.WeightRepository
import com.faigenbloom.spartaculous.common.MESSAGE
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException

fun Route.weightRoutes(weightRepo: WeightRepository) {
    route("/api/weight") {

        fun userId(call: ApplicationCall): String = call.request.headers["X-User-Id"] ?: "demo-user"

        get {
            call.respond(
                status = HttpStatusCode.OK,
                message = weightRepo.list(userId(call))
            )
        }

        post {
            try {
                val req = call.receive<AddWeightRequest>()

                if (req.valueKg <= 0f || req.valueKg > 400f) {
                    call.respond(HttpStatusCode.BadRequest, MESSAGE("Invalid valueKg: ${'$'}{req.valueKg}"))
                    return@post
                }

                val created = weightRepo.add(userId(call), req)
                call.respond(HttpStatusCode.Created, created)
            } catch (e: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, MESSAGE(e.message ?: "Invalid request body"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, MESSAGE(e.message ?: "Bad request"))
            } catch (e: Throwable) {
                call.application.environment.log.error("POST /api/weight failed", e)
                call.respond(HttpStatusCode.InternalServerError, MESSAGE(e.message ?: "Internal server error"))
            }
        }

        delete {
            try {
                val count = weightRepo.clear(userId(call))
                call.respond(HttpStatusCode.OK, mapOf("deleted" to count))
            } catch (e: Throwable) {
                call.application.environment.log.error("DELETE /api/weight failed", e)
                call.respond(HttpStatusCode.InternalServerError, MESSAGE(e.message ?: "Internal server error"))
            }
        }
    }
}
