package com.faigenbloom.spartaculous.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.faigenbloom.spartaculous.weight.WeightRepository
import com.faigenbloom.spartaculous.routing.weightRoutes
import com.faigenbloom.spartaculous.food.FoodRepository
import com.faigenbloom.spartaculous.food.InMemoryFoodRepository
import com.faigenbloom.spartaculous.routing.foodRoutes
import com.faigenbloom.spartaculous.training.TrainingRepository
import com.faigenbloom.spartaculous.training.TrainingPlansRepository
import com.faigenbloom.spartaculous.routing.trainingRoutes
import com.faigenbloom.spartaculous.bodyfat.BodyFatRepository
import com.faigenbloom.spartaculous.routing.bodyFatRoutes
import com.faigenbloom.spartaculous.training.InMemoryTrainingRepository
import com.faigenbloom.spartaculous.training.SYSTEM_EXERCISES
import com.faigenbloom.spartaculous.config.Config
import com.faigenbloom.spartaculous.goals.GoalsRepository
import com.faigenbloom.spartaculous.goals.goalsRoutes
import com.faigenbloom.spartaculous.settings.SettingsRepository
import com.faigenbloom.spartaculous.settings.settingsRoutes
import com.faigenbloom.spartaculous.hydration.HydrationRepository
import com.faigenbloom.spartaculous.hydration.hydrationRoutes
import com.faigenbloom.spartaculous.media.mediaRoutes
import com.faigenbloom.spartaculous.nutrition.nutritionRoutes
import com.faigenbloom.spartaculous.recommendations.RecommendationsRepository
import com.faigenbloom.spartaculous.recommendations.recommendationsRoutes
import com.faigenbloom.spartaculous.analytics.analyticsRoutes
import com.faigenbloom.spartaculous.measurements.MeasurementsRepository
import com.faigenbloom.spartaculous.routing.measurementsRoutes
import com.faigenbloom.spartaculous.users.usersRoutes
import com.faigenbloom.spartaculous.premium.premiumRoutes
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
private data class SeedDailyDataResult(
    val userId: String,
    val entriesInserted: Int,
    val mealsInserted: Int
)

@Serializable
private data class SeedDailyDataResponse(
    val entriesPerUser: Int,
    val mealsPerUser: Int,
    val results: List<SeedDailyDataResult>
)

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("OK")
        }

        // Weight feature (Mongo via Koin)
        val weightRepo by inject<WeightRepository>()
        weightRoutes(weightRepo)

        // Food feature (in-memory for now)
        val foodRepo by inject<FoodRepository>()
        foodRoutes(foodRepo)

        // Training feature (in-memory for now)
        val trainingRepo by inject<TrainingRepository>()
        val trainingPlansRepo by inject<TrainingPlansRepository>()
        trainingRoutes(trainingRepo, trainingPlansRepo)

        // Body Fat feature
        val bodyFatRepo by inject<BodyFatRepository>()
        bodyFatRoutes(bodyFatRepo)

        // Goals feature
        val goalsRepo by inject<GoalsRepository>()
        goalsRoutes(goalsRepo)

        // Recommendations
        val recRepo by inject<RecommendationsRepository>()
        recommendationsRoutes(recRepo)

        // Analytics dashboard
        analyticsRoutes()

        // Measurements feature
        val measRepo by inject<MeasurementsRepository>()
        measurementsRoutes(measRepo)

        // Settings feature
        val settingsRepo by inject<SettingsRepository>()
        settingsRoutes(settingsRepo)

        // Hydration feature
        val hydrationRepo by inject<HydrationRepository>()
        hydrationRoutes(hydrationRepo)

        // Media upload
        mediaRoutes()

        // Nutrition OCR
        nutritionRoutes()

        // Premium billing (Google Play verification)
        premiumRoutes()

        // Users (merge guest -> account)
        usersRoutes()

        // Dev-only admin: trigger system exercises seeding
        if (Config.isDev) {
            post("/api/admin/training/seed-exercises") {
                val inMemory = trainingRepo as? InMemoryTrainingRepository
                if (inMemory != null) {
                    val report = inMemory.seedSystemExercises(SYSTEM_EXERCISES)
                    call.respond(mapOf(
                        "inserted" to report.inserted,
                        "updated" to report.updated,
                        "skipped" to report.skipped
                    ))
                } else {
                    call.respond(mapOf("message" to "Not supported by current repository"))
                }
            }

            post("/api/admin/seed-daily-data") {
                val trainingInMemory = trainingRepo as? InMemoryTrainingRepository
                val foodInMemory = foodRepo as? InMemoryFoodRepository
                if (trainingInMemory == null || foodInMemory == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Seeding supported only with in-memory repositories")
                    )
                    return@post
                }

                val entriesPerUser = call.request.queryParameters["entries"]?.toIntOrNull()?.coerceAtLeast(0) ?: 20
                val mealsPerUser = call.request.queryParameters["meals"]?.toIntOrNull()?.coerceAtLeast(0) ?: 20

                val userIds = (trainingInMemory.knownUserIds() + foodInMemory.knownUserIds()).filter { it.isNotBlank() }.toMutableSet()
                if (userIds.isEmpty()) {
                    userIds += "demo-user"
                }

                val results = userIds.sorted().map { uid ->
                    val entriesInserted = if (entriesPerUser > 0) trainingInMemory.seedDailyEntries(uid, entriesPerUser) else 0
                    val mealsInserted = if (mealsPerUser > 0) foodInMemory.seedTodayMeals(uid, mealsPerUser) else 0
                    SeedDailyDataResult(
                        userId = uid,
                        entriesInserted = entriesInserted,
                        mealsInserted = mealsInserted
                    )
                }

                call.respond(
                    SeedDailyDataResponse(
                        entriesPerUser = entriesPerUser,
                        mealsPerUser = mealsPerUser,
                        results = results
                    )
                )
            }
        }
    }
}
