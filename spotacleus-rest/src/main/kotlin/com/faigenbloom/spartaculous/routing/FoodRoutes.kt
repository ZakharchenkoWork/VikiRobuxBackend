package com.faigenbloom.spartaculous.routing

import com.faigenbloom.spartaculous.food.*
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

private const val USE_TEMPORARY_MEALS = false

fun Route.foodRoutes(foodRepo: FoodRepository) {
    route("/api/food") {
        suspend fun uidOr401(call: ApplicationCall): String? {
            val uid = call.request.headers["X-User-Id"]?.trim()
            if (uid.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing X-User-Id header"))
                return null
            }
            return uid
        }

        // For tests: wipe everything for user
        delete {
            val uid = uidOr401(call) ?: return@delete
            foodRepo.clearUser(uid)
            call.respond(HttpStatusCode.NoContent)
        }

        route("/meals") {
            get {
                val uid = uidOr401(call) ?: return@get
                val response = if (USE_TEMPORARY_MEALS) temporaryMeals() else foodRepo.listMeals(uid)
                call.respond(HttpStatusCode.OK, response)
            }

            post {
                val uid = uidOr401(call) ?: return@post
                val ct = call.request.headers["Content-Type"]
                val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
                val req = try {
                    Json { ignoreUnknownKeys = true; isLenient = true }
                        .decodeFromString<CreateMealRequest>(raw)
                } catch (e: SerializationException) {
                    call.application.environment.log.error("POST /api/food/meals deserialization failed, CT=${ct}, raw='${raw}'", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
                    return@post
                }

                val name = req.name.trim()
                if (name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid name"))
                    return@post
                }
                if (req.grams <= 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid grams"))
                    return@post
                }
                if (req.calories < 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid calories"))
                    return@post
                }

                val created = foodRepo.addMeal(uid, req)
                call.respond(HttpStatusCode.Created, created)
            }

            route("/{mealId}") {
                put {
                    val uid = uidOr401(call) ?: return@put
                    val mealId = call.parameters.getOrFail("mealId")
                    val ct = call.request.headers["Content-Type"]
val raw = try { call.receiveText() } catch (_: Throwable) { "<unavailable>" }
val req = try {
    Json { ignoreUnknownKeys = true; isLenient = true }
        .decodeFromString<UpdateMealRequest>(raw)
} catch (e: SerializationException) {
    call.application.environment.log.error("PUT /api/food/meals/{mealId} deserialization failed, CT=${ct}, raw='${raw}'", e)
    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (e.message ?: "Invalid request body")))
    return@put
}
                    val updated = foodRepo.updateMeal(uid, mealId, req)
                    call.respond(HttpStatusCode.OK, updated)
                }

                delete {
                    val uid = uidOr401(call) ?: return@delete
                    val mealId = call.parameters.getOrFail("mealId")
                    foodRepo.deleteMeal(uid, mealId)
                    call.respond(HttpStatusCode.NoContent)
                }

                route("/ingredients") {
                    get {
                        val uid = uidOr401(call) ?: return@get
                        val mealId = call.parameters.getOrFail("mealId")
                        call.respond(HttpStatusCode.OK, foodRepo.listIngredients(uid, mealId))
                    }

                    post {
                        val uid = uidOr401(call) ?: return@post
                        val mealId = call.parameters.getOrFail("mealId")
                        val req = call.receive<CreateIngredientRequest>()

                        val name = req.name.trim()
                        if (name.isBlank()) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid name"))
                            return@post
                        }
                        if (req.grams <= 0) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid grams"))
                            return@post
                        }
                        if (req.calories < 0) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid calories"))
                            return@post
                        }

                        val created = foodRepo.addIngredient(uid, mealId, req)
                        call.respond(HttpStatusCode.Created, created)
                    }

                    route("/{ingredientId}") {
                        put {
                            val uid = uidOr401(call) ?: return@put
                            val mealId = call.parameters.getOrFail("mealId")
                            val ingredientId = call.parameters.getOrFail("ingredientId")
                            val req = call.receive<UpdateIngredientRequest>()
                            val updated = foodRepo.updateIngredient(uid, mealId, ingredientId, req)
                            call.respond(HttpStatusCode.OK, updated)
                        }

                        delete {
                            val uid = uidOr401(call) ?: return@delete
                            val mealId = call.parameters.getOrFail("mealId")
                            val ingredientId = call.parameters.getOrFail("ingredientId")
                            foodRepo.deleteIngredient(uid, mealId, ingredientId)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }
                }
            }
        }
    }
}

private fun temporaryMeals(): List<FoodMealDto> {
    val now = Instant.now().toEpochMilli()
    return List(20) { idx ->
        val timestamp = now - idx * 45L * 60L * 1000L
        FoodMealDto(
            id = "temp-meal-${idx + 1}",
            name = "Test Meal ${idx + 1}",
            grams = 180 + (idx % 3) * 40,
            calories = 320 + (idx % 4) * 30,
            proteins = 20 + (idx % 5) * 3,
            fats = 12 + (idx % 4) * 2,
            carbs = 45 + (idx % 6) * 5,
            sugar = 6 + (idx % 2) * 2,
            sodium = 180 + (idx % 5) * 40,
            potassium = 240 + (idx % 5) * 35,
            calcium = 60 + (idx % 4) * 8,
            magnesium = 40 + (idx % 3) * 6,
            iron = 8 + (idx % 3) * 2,
            photoUrl = null,
            imageUrl = null,
            ingredientsCount = (idx % 4) + 1,
            isSportsNutrition = idx % 6 == 0,
            recordedAtEpochMillis = timestamp
        )
    }
}
