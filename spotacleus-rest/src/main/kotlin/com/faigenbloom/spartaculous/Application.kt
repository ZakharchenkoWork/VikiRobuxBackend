package com.faigenbloom.spartaculous

import com.faigenbloom.spartaculous.config.Config
import com.faigenbloom.spartaculous.config.appModule
import com.faigenbloom.spartaculous.routing.configureRouting
import com.faigenbloom.spartaculous.auth.AuthPlugin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.util.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import io.ktor.server.plugins.BadRequestException
import com.faigenbloom.spartaculous.common.respondError
import com.faigenbloom.spartaculous.training.InMemoryTrainingRepository
import com.faigenbloom.spartaculous.training.SYSTEM_EXERCISES
import com.faigenbloom.spartaculous.training.TrainingRepository
import com.faigenbloom.spartaculous.training.TrainingPlansMongo
import com.faigenbloom.spartaculous.bodyfat.BodyFatMongo
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.get
import org.koin.core.logger.Level as KoinLevel
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level as LogLevel

fun main() {
    embeddedServer(
        Netty,
        port = Config.port,
        host = Config.host,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger(level = if (Config.isDev) KoinLevel.INFO else KoinLevel.ERROR)
        modules(appModule)
    }

    // Guard dangerous clear-all endpoint in non-dev envs
    intercept(ApplicationCallPipeline.Call) {
        if (!Config.isDev && call.request.local.method == HttpMethod.Delete && call.request.uri == "/api/training") {
            call.respondError(
                status = HttpStatusCode.Forbidden,
                code = "FORBIDDEN",
                message = "Clear is allowed only in dev"
            )
            finish()
            return@intercept
        }
    }

    install(DefaultHeaders)
    install(AuthPlugin)
    install(CallLogging) {
        level = if (Config.isDev) LogLevel.INFO else LogLevel.ERROR
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
                coerceInputValues = true
            }
        )
    }
    install(StatusPages) {
        exception<ContentTransformationException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = "INVALID_JSON",
                message = cause.message ?: "Invalid request body"
            )
        }
        exception<SerializationException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = "INVALID_JSON",
                message = cause.message ?: "Invalid request body"
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = "VALIDATION_ERROR",
                message = cause.message ?: "Bad request"
            )
        }
        exception<NoSuchElementException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.NotFound,
                code = "NOT_FOUND",
                message = cause.message ?: "Not found"
            )
        }
        exception<SecurityException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.Forbidden,
                code = "FORBIDDEN",
                message = cause.message ?: "Forbidden"
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.Conflict,
                code = "CONFLICT",
                message = cause.message ?: "Conflict"
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = "BAD_REQUEST",
                message = cause.message ?: "Bad request"
            )
        }
        exception<Throwable> { call, cause ->
            this@module.log.error("Unhandled error", cause)
            val details = if (Config.isDev || Config.exposeErrorDetails) mapOf(
                "exception" to (cause::class.qualifiedName ?: cause::class.simpleName ?: "Throwable"),
                "stackTrace" to cause.stackTraceToString()
            ) else null
            call.respondError(
                status = HttpStatusCode.InternalServerError,
                code = "INTERNAL_ERROR",
                message = cause.message ?: "Internal server error",
                details = details
            )
        }
    }

    // Seed system exercises on startup (idempotent). Only applies to in-memory repo.
    try {
        val trainingRepo = get<TrainingRepository>()
        if (trainingRepo is InMemoryTrainingRepository) {
            trainingRepo.seedSystemExercises(SYSTEM_EXERCISES)
        }
    } catch (_: Throwable) {
        // If training repo is not bound yet or seeding fails, app still starts; admin route can seed later
    }

    // Initialize training plans indexes
    try {
        val plansMongo = get<TrainingPlansMongo>()
        runBlocking {
            plansMongo.ensureIndexes()
        }
        log.info("Training plans indexes initialized")
    } catch (e: Throwable) {
        log.error("Failed to initialize training plans indexes", e)
    }

    configureRouting()
}

