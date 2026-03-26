package com.faigenbloom.spartaculous.users

import com.faigenbloom.spartaculous.common.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.litote.kmongo.coroutine.CoroutineDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document
import com.mongodb.MongoWriteException

fun Route.usersRoutes() {
    val db by inject<CoroutineDatabase>()

    route("/api/users") {
        post("/merge") {
            val newUid = call.request.headers["X-User-Id"]?.trim()
            val oldUid = call.request.headers["X-Anonymous-Id"]?.trim()
            if (newUid.isNullOrBlank() || oldUid.isNullOrBlank()) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    code = "BAD_REQUEST",
                    message = "Headers X-User-Id and X-Anonymous-Id are required"
                )
                return@post
            }
            if (newUid == oldUid) {
                call.respond(HttpStatusCode.NoContent)
                return@post
            }

            // Helper: simple bulk update for collections without conflicting unique indexes
            suspend fun migrateSimple(collection: String) {
                val col = db.getCollection<Document>(collection)
                col.updateMany(Filters.eq("userId", oldUid), Updates.set("userId", newUid))
            }

            // Measurements (entries) have unique index on (userId, type, dayStartEpochMillis) -> migrate doc-by-doc
            suspend fun migrateMeasurementsEntries() {
                val col = db.getCollection<Document>("measurements")
                val oldDocs = col.find(Filters.eq("userId", oldUid)).toList()
                for (d in oldDocs) {
                    try {
                        d["userId"] = newUid
                        col.replaceOne(Filters.eq("_id", d["_id"]), d)
                    } catch (e: MongoWriteException) {
                        // Duplicate due to unique index -> drop old doc
                        if (e.code == 11000) {
                            col.deleteOne(Filters.eq("_id", d["_id"]))
                        } else throw e
                    }
                }
            }

            // Measurements goal: ensure single goal per user
            suspend fun migrateMeasurementsGoal() {
                val col = db.getCollection<Document>("measurements_goals")
                // remove any existing goal for new user to avoid duplicates
                col.deleteMany(Filters.eq("userId", newUid))
                col.updateMany(Filters.eq("userId", oldUid), Updates.set("userId", newUid))
            }

            // Goals-related collections
            suspend fun migrateGoals() {
                migrateSimple("goals_core")
                migrateSimple("goals_training")
                migrateSimple("goals_recovery")
                migrateSimple("goals_recovery_logs")
                migrateSimple("goals_plan")
                migrateSimple("goals_templates")
            }

            // Other collections
            suspend fun migrateOthers() {
                migrateSimple("weights")
                migrateSimple("nutrition_scans")
                migrateSimple("recommendations")
                migrateSimple("premium_entitlements")
            }

            kotlinx.coroutines.runBlocking {
                migrateMeasurementsEntries()
                migrateMeasurementsGoal()
                migrateGoals()
                migrateOthers()
                // Finally, clean any leftovers for old user in migrated collections
                val toClean = listOf(
                    "measurements", "measurements_goals",
                    "goals_core", "goals_training", "goals_recovery", "goals_recovery_logs", "goals_plan", "goals_templates",
                    "weights", "nutrition_scans", "recommendations", "premium_entitlements"
                )
                for (c in toClean) {
                    db.getCollection<Document>(c).deleteMany(Filters.eq("userId", oldUid))
                }
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
