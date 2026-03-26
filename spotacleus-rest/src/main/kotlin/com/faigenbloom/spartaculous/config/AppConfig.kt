package com.faigenbloom.spartaculous.config

import com.faigenbloom.spartaculous.ai.GeminiService
import com.faigenbloom.spartaculous.service.FirebaseService
import com.faigenbloom.spartaculous.vision.VisionService
import com.faigenbloom.spartaculous.nutrition.NutritionScanRepository
import com.faigenbloom.spartaculous.nutrition.MongoNutritionScanRepository
import com.faigenbloom.spartaculous.nutrition.NutritionRulesRepository
import com.faigenbloom.spartaculous.nutrition.InMemoryNutritionRulesRepository
import com.faigenbloom.spartaculous.nutrition.NutritionRulesEngine
import com.faigenbloom.spartaculous.nutrition.MongoNutritionRulesRepository
import com.faigenbloom.spartaculous.nutrition.NutritionRuleTrainer
import com.faigenbloom.spartaculous.weight.WeightDataSource
import com.faigenbloom.spartaculous.weight.WeightMongo
import com.faigenbloom.spartaculous.weight.WeightMongoRepository
import com.faigenbloom.spartaculous.weight.WeightRepository
import com.faigenbloom.spartaculous.measurements.MeasurementsRepository
import com.faigenbloom.spartaculous.measurements.MeasurementsMongoRepository
import com.faigenbloom.spartaculous.food.FoodRepository
import com.faigenbloom.spartaculous.food.InMemoryFoodRepository
import com.faigenbloom.spartaculous.training.TrainingRepository
import com.faigenbloom.spartaculous.training.InMemoryTrainingRepository
import com.faigenbloom.spartaculous.training.TrainingPlansRepository
import com.faigenbloom.spartaculous.training.MongoTrainingPlansRepository
import com.faigenbloom.spartaculous.training.TrainingPlansMongo
import com.faigenbloom.spartaculous.bodyfat.BodyFatRepository
import com.faigenbloom.spartaculous.bodyfat.MongoBodyFatRepository
import com.faigenbloom.spartaculous.bodyfat.BodyFatMongo
import com.faigenbloom.spartaculous.goals.GoalsRepository
import com.faigenbloom.spartaculous.goals.InMemoryGoalsRepository
import com.faigenbloom.spartaculous.goals.MongoGoalsRepository
import com.faigenbloom.spartaculous.recommendations.RecommendationsRepository
import com.faigenbloom.spartaculous.recommendations.MongoRecommendationsRepository
import com.faigenbloom.spartaculous.settings.SettingsRepository
import com.faigenbloom.spartaculous.settings.InMemorySettingsRepository
import com.faigenbloom.spartaculous.hydration.HydrationRepository
import com.faigenbloom.spartaculous.hydration.InMemoryHydrationRepository
import com.faigenbloom.spartaculous.premium.PremiumRepository
import com.faigenbloom.spartaculous.premium.MongoPremiumRepository
import com.faigenbloom.spartaculous.premium.PremiumService
import com.faigenbloom.spartaculous.premium.GooglePlayService
import com.mongodb.MongoClientSettings
import org.koin.dsl.module
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

val appModule = module {
    single<CoroutineClient> {
        val connectionString = System.getenv("MONGODB_URI") ?: "mongodb://localhost:27017"
        val settings = MongoClientSettings.builder()
            .applyConnectionString(com.mongodb.ConnectionString(connectionString))
            .build()
        KMongo.createClient(settings).coroutine
    }

    single<CoroutineDatabase> {
        val dbName = System.getenv("MONGODB_DB") ?: "spartaculous"
        get<CoroutineClient>().getDatabase(dbName)
    }

    single { FirebaseService() }
    single { VisionService() }
    // Gemini (Vertex AI) for nutrition parsing from images
    single { GeminiService() }
    // Nutrition OCR scan storage (Mongo)
    single<NutritionScanRepository> { MongoNutritionScanRepository(get()) }
    // Nutrition dynamic parsing rules (Mongo + engine)
    single<NutritionRulesRepository> {
        val source = System.getenv("NUTRITION_RULES_SOURCE")?.lowercase()?.trim()
        if (source == null || source == "mongo") MongoNutritionRulesRepository(get()) else InMemoryNutritionRulesRepository()
    }
    single { NutritionRulesEngine(get()) }
    single { NutritionRuleTrainer(get(), get()) }
    // Weight feature
    single { WeightMongo(get<CoroutineDatabase>()) }
    single { WeightDataSource(get()) }
    single<WeightRepository> { WeightMongoRepository(get()) }
    // Measurements feature (Mongo)
    single<MeasurementsRepository> { MeasurementsMongoRepository(get()) }
    // Food feature (in-memory for now)
    single<FoodRepository> { InMemoryFoodRepository() }
    // Training feature (in-memory for now)
    single<TrainingRepository> { InMemoryTrainingRepository() }
    // Training plans (Mongo)
    single { TrainingPlansMongo(get<CoroutineDatabase>()) }
    single<TrainingPlansRepository> { MongoTrainingPlansRepository(get()) }
    // Body Fat feature (Mongo)
    single { BodyFatMongo(get<CoroutineDatabase>()) }
    single<BodyFatRepository> { MongoBodyFatRepository(get()) }
    // Goals feature (Mongo)
    single<GoalsRepository> { MongoGoalsRepository(get()) }
    // Recommendations (Mongo)
    single<RecommendationsRepository> { MongoRecommendationsRepository(get()) }
    // Settings feature (in-memory)
    single<SettingsRepository> { InMemorySettingsRepository() }
    // Hydration feature (in-memory)
    single<HydrationRepository> { InMemoryHydrationRepository() }
    // Premium (Mongo + cached service)
    single<PremiumRepository> { MongoPremiumRepository(get()) }
    single { PremiumService(get()) }
    single { GooglePlayService(System.getenv("GOOGLE_PLAY_CREDENTIALS_FILE")) }
    // TODO register data sources when models are ready
}

object Config {
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8181
    val host: String = System.getenv("HOST") ?: "0.0.0.0"
    val isDev: Boolean = (System.getenv("ENV") ?: "dev") == "dev"
    val exposeErrorDetails: Boolean = (System.getenv("EXPOSE_ERROR_DETAILS") ?: "false").equals("true", ignoreCase = true)
}
