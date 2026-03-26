package com.faigenbloom.spartaculous.nutrition

import com.faigenbloom.spartaculous.nutrition.NutritionScanParser.NutritionScanDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import com.mongodb.client.model.ReplaceOptions
import java.time.Instant
import java.util.UUID

@Serializable
enum class RuleMode { @SerialName("stable") STABLE, @SerialName("experimental") EXPERIMENTAL }

@Serializable
data class NutritionRule(
    val ruleId: String,
    val version: Int = 1,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val weight: Double = 1.0,
    val mode: RuleMode = RuleMode.STABLE,

    // scope
    val field: String, // calories | proteinG | carbsG | fatG | sugarG | fiberG | sodiumMg
    val locales: List<String> = emptyList(), // e.g. ["ru", "en"], empty = all

    // matching
    val includePatterns: List<String>, // regex strings with one capturing group for value
    val excludePatterns: List<String> = emptyList(),
    val contextTokens: List<String> = emptyList(), // tokens that should be present in the same/adjacent line

    // normalization
    val unitMap: Map<String, String> = emptyMap(), // e.g. {"г":"g","мг":"mg"}
    val decimalComma: Boolean = true,
    val toInt: Boolean = false,
    val mgToG: Boolean = false,
    val gToMg: Boolean = false,

    // audit
    val createdBy: String? = null,
    val createdAtEpochMs: Long = Instant.now().toEpochMilli()
)

interface NutritionRulesRepository {
    suspend fun getActiveRules(): List<NutritionRule>
    // default no-op upserts to keep test fakes simple
    suspend fun upsertRule(rule: NutritionRule) {}
    suspend fun upsertRules(rules: List<NutritionRule>) {
        for (r in rules) upsertRule(r)
    }
}

class MongoNutritionRulesRepository(private val db: CoroutineDatabase) : NutritionRulesRepository {
    private val col = db.getCollection<NutritionRule>("nutrition_rules")
    override suspend fun getActiveRules(): List<NutritionRule> {
        val all = col.find().toList()
        return all.filter { it.enabled }.sortedWith(compareByDescending<NutritionRule> { it.priority }.thenBy { it.ruleId })
    }

    override suspend fun upsertRule(rule: NutritionRule) {
        col.replaceOne(NutritionRule::ruleId eq rule.ruleId, rule, ReplaceOptions().upsert(true))
    }

    override suspend fun upsertRules(rules: List<NutritionRule>) {
        for (r in rules) upsertRule(r)
    }
}

class NutritionRulesEngine(private val repo: NutritionRulesRepository) {
    // Simple time-based cache
    @Volatile
    private var cachedRules: List<NutritionRule> = emptyList()
    @Volatile
    private var nextReloadAtMs: Long = 0L
    private val reloadIntervalMs: Long = 60_000 // 1 min

    private suspend fun ensureRules() {
        val now = System.currentTimeMillis()
        if (now >= nextReloadAtMs || cachedRules.isEmpty()) {
            cachedRules = repo.getActiveRules()
            nextReloadAtMs = now + reloadIntervalMs
        }
    }

    fun reloadNow() {
        nextReloadAtMs = 0L
    }

    suspend fun snapshotRuleIds(): List<String> {
        ensureRules()
        return cachedRules.map { it.ruleId }
    }

    data class ParsedField(val value: Double?, val ruleId: String?)
    data class ParsedResult(
        val calories: ParsedField = ParsedField(null, null),
        val proteinG: ParsedField = ParsedField(null, null),
        val carbsG: ParsedField = ParsedField(null, null),
        val fatG: ParsedField = ParsedField(null, null),
        val sugarG: ParsedField = ParsedField(null, null),
        val fiberG: ParsedField = ParsedField(null, null),
        val sodiumMg: ParsedField = ParsedField(null, null)
    ) {
        fun toDtoWith(scanId: String, fallback: NutritionScanDto?): NutritionScanDto {
            fun v(name: String): Double? = when (name) {
                "proteinG" -> proteinG.value
                "carbsG" -> carbsG.value
                "fatG" -> fatG.value
                "sugarG" -> sugarG.value
                "fiberG" -> fiberG.value
                else -> null
            }
            return NutritionScanDto(
                scanId = scanId,
                ingredientId = null,
                calories = (calories.value ?: fallback?.calories?.toDouble())?.toInt(),
                proteinG = v("proteinG") ?: fallback?.proteinG,
                carbsG = v("carbsG") ?: fallback?.carbsG,
                fatG = v("fatG") ?: fallback?.fatG,
                sugarG = v("sugarG") ?: fallback?.sugarG,
                fiberG = v("fiberG") ?: fallback?.fiberG,
                sodiumMg = (sodiumMg.value ?: fallback?.sodiumMg?.toDouble())?.toInt()
            )
        }
        fun appliedRulesMap(): Map<String,String> = buildMap {
            calories.ruleId?.let { put("calories", it) }
            proteinG.ruleId?.let { put("proteinG", it) }
            carbsG.ruleId?.let { put("carbsG", it) }
            fatG.ruleId?.let { put("fatG", it) }
            sugarG.ruleId?.let { put("sugarG", it) }
            fiberG.ruleId?.let { put("fiberG", it) }
            sodiumMg.ruleId?.let { put("sodiumMg", it) }
        }
    }

    suspend fun parse(textRaw: String, locale: String? = null): ParsedResult {
        ensureRules()
        val text = textRaw.lowercase()
        val lines = text.split('\n', '\r').map { it.trim() }.filter { it.isNotBlank() }
        fun matchField(fieldName: String): ParsedField {
            val rules = cachedRules.filter { it.field == fieldName && (it.locales.isEmpty() || locale == null || it.locales.contains(locale)) }
            for (rule in rules) {
                val includeRegexes = rule.includePatterns.map { it.toRegex(RegexOption.IGNORE_CASE) }
                val excludeRegexes = rule.excludePatterns.map { it.toRegex(RegexOption.IGNORE_CASE) }
                // scan lines: prefer same-line match; if not found, consider next line concatenation
                for (i in lines.indices) {
                    val line = lines[i]
                    fun tryRegion(region: String): ParsedField? {
                        if (rule.contextTokens.isNotEmpty() && !rule.contextTokens.any { token -> region.contains(token) }) return null
                        if (excludeRegexes.any { it.containsMatchIn(region) }) return null
                        val m = includeRegexes.firstNotNullOfOrNull { rx -> rx.find(region) } ?: return null
                        val raw = m.groupValues.getOrNull(1)?.trim().orEmpty()
                        val norm1 = if (rule.decimalComma) raw.replace(',', '.') else raw
                        val num = norm1.toDoubleOrNull() ?: return null
                        var valNum = num
                        if (rule.mgToG) valNum = valNum / 1000.0
                        if (rule.gToMg) valNum = valNum * 1000.0
                        return ParsedField(value = if (rule.toInt) kotlin.math.round(valNum).toDouble() else valNum, ruleId = rule.ruleId)
                    }
                    // 1) same line
                    tryRegion(line)?.let { return it }
                    // 2) line + next line
                    lines.getOrNull(i + 1)?.let { next ->
                        val region2 = buildString { append(line).append(" \n ").append(next) }
                        tryRegion(region2)?.let { return it }
                    }
                }
            }
            return ParsedField(null, null)
        }
        return ParsedResult(
            calories = matchField("calories"),
            proteinG = matchField("proteinG"),
            carbsG = matchField("carbsG"),
            fatG = matchField("fatG"),
            sugarG = matchField("sugarG"),
            fiberG = matchField("fiberG"),
            sodiumMg = matchField("sodiumMg")
        )
    }

    suspend fun parseToDto(textRaw: String, locale: String? = null): Pair<NutritionScanDto, Map<String,String>> {
        val scanId = UUID.randomUUID().toString()
        val parsed = parse(textRaw, locale)
        // fallback: use legacy static parser if everything is null
        val any = listOf(
            parsed.calories.value, parsed.proteinG.value, parsed.carbsG.value,
            parsed.fatG.value, parsed.sugarG.value, parsed.fiberG.value, parsed.sodiumMg.value
        ).any { it != null }
        val fallback = if (!any) NutritionScanParser.parseNutritionScan(textRaw) else null
        val dto = parsed.toDtoWith(scanId, fallback)
        return dto to parsed.appliedRulesMap()
    }
}
