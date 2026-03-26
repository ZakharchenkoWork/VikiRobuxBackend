package com.faigenbloom.spartaculous.nutrition

import com.faigenbloom.spartaculous.nutrition.NutritionScanParser.NutritionScanDto
import com.faigenbloom.spartaculous.vision.VisionService
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import java.time.Instant

@Serializable
data class IngredientCorrectionDto(
    val ingredientId: String? = null,
    val name: String? = null,
    val calories: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val sugarG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Int? = null
)

@Serializable
data class NutritionScanRecord(
    val scanId: String,
    val userId: String,
    val createdAtEpochMs: Long,
    val ocrText: String,
    val parsed: NutritionScanDto,
    val appliedRules: Map<String, String>? = null,
    val visionFacts: VisionService.NutritionFacts? = null,
    val correction: IngredientCorrectionDto? = null
)

interface NutritionScanRepository {
    suspend fun saveScan(record: NutritionScanRecord)
    suspend fun saveCorrection(scanId: String, userId: String, correction: IngredientCorrectionDto): Boolean
    suspend fun getScan(scanId: String, userId: String): NutritionScanRecord?
}

class MongoNutritionScanRepository(private val db: CoroutineDatabase) : NutritionScanRepository {
    private val col = db.getCollection<NutritionScanRecord>("nutrition_scans")

    override suspend fun saveScan(record: NutritionScanRecord) {
        col.insertOne(record)
    }

    override suspend fun saveCorrection(scanId: String, userId: String, correction: IngredientCorrectionDto): Boolean {
        val existing = col.findOne(NutritionScanRecord::scanId eq scanId)
        if (existing == null || existing.userId != userId) return false
        val updated = existing.copy(correction = correction)
        val res = col.replaceOne(NutritionScanRecord::scanId eq scanId, updated)
        return res.matchedCount == 1L
    }

    override suspend fun getScan(scanId: String, userId: String): NutritionScanRecord? {
        val rec = col.findOne(NutritionScanRecord::scanId eq scanId)
        return if (rec?.userId == userId) rec else null
    }
}
