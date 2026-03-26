package com.faigenbloom.spartaculous.nutrition

import kotlin.math.abs

class NutritionRuleTrainer(
    private val rulesRepo: NutritionRulesRepository,
    private val rulesEngine: NutritionRulesEngine
) {
    // Lightweight heuristic: for каждого поля ищем строку с подходящими ключевыми словами и единицами,
    // создаём правило с приоритетом выше дефолтов.
    suspend fun trainFromCorrection(record: NutritionScanRecord) {
        val corr = record.correction ?: return
        val text = record.ocrText.lowercase()
        val lines = text.split('\n', '\r').map { it.trim() }.filter { it.isNotBlank() }

        val newRules = mutableListOf<NutritionRule>()

        fun mkRuleId(prefix: String) = "auto_${prefix}_${record.scanId.take(8)}"

        data class Spec(
            val field: String,
            val value: Number?,
            val unitTokens: List<String>,
            val contextTokens: List<String>,
            val toInt: Boolean = false
        )

        val specs = listOf(
            Spec("calories", corr.calories, emptyList(), listOf("ккал","kcal","calorie","calories","энерг","energy"), toInt = true),
            Spec("proteinG", corr.proteinG, listOf("g","г"), listOf("бел","protein")),
            Spec("carbsG", corr.carbsG, listOf("g","г"), listOf("углев","carb","carbohydrate")),
            Spec("fatG", corr.fatG, listOf("g","г"), listOf("жир","fat")),
            Spec("sugarG", corr.sugarG, listOf("g","г"), listOf("сахар","sugar","sugars")),
            Spec("fiberG", corr.fiberG, listOf("g","г"), listOf("клетчат","волокна","fiber")),
            Spec("sodiumMg", corr.sodiumMg, listOf("mg","мг"), listOf("натрий","sodium"), toInt = true)
        )

        fun differs(field: String, corrected: Number?): Boolean {
            if (corrected == null) return false
            val parsed = when (field) {
                "calories" -> record.parsed.calories?.toDouble()
                "proteinG" -> record.parsed.proteinG
                "carbsG" -> record.parsed.carbsG
                "fatG" -> record.parsed.fatG
                "sugarG" -> record.parsed.sugarG
                "fiberG" -> record.parsed.fiberG
                "sodiumMg" -> record.parsed.sodiumMg?.toDouble()
                else -> null
            }
            if (parsed == null) return true
            return abs(parsed - corrected.toDouble()) > 1e-3
        }

        for (spec in specs) {
            if (!differs(spec.field, spec.value ?: continue)) continue
            val hitIndex = lines.indexOfFirst { line ->
                (spec.contextTokens.isEmpty() || spec.contextTokens.any { t -> line.contains(t) }) &&
                (spec.unitTokens.isEmpty() || spec.unitTokens.any { u -> line.contains(u) })
            }
            if (hitIndex == -1) continue
            val ctx = spec.contextTokens.take(3)
            val include = when (spec.field) {
                "calories" -> listOf(
                    "(?i)(?:ккал|kcal)\\D{0,10}(\\d{1,4})",
                    "(?i)(\\d{1,4})\\s*(?:ккал|kcal)",
                    "(?i)(?:calories|energy)\\s*[:=\\-]?\\s*(\\d{1,4})",
                    "(?i)энерг[а-я]*\\D{0,10}(\\d{1,4})"
                )
                "sodiumMg" -> listOf(
                    "(?i)(?:натрий|натрия|sodium)\\s*:?\\s*(\\d{1,5})\\s*(?:mg|мг)"
                )
                else -> listOf(
                    "(?i)(?:${ctx.joinToString("|")})[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:${spec.unitTokens.joinToString("|")})"
                )
            }
            newRules += NutritionRule(
                ruleId = mkRuleId(spec.field),
                field = spec.field,
                includePatterns = include,
                excludePatterns = listOf("%"),
                contextTokens = ctx,
                toInt = spec.toInt,
                priority = 500, // выше дефолтов
                enabled = true,
                mode = RuleMode.STABLE,
                locales = emptyList(),
                weight = 1.0
            )
        }

        if (newRules.isNotEmpty()) {
            rulesRepo.upsertRules(newRules)
            rulesEngine.reloadNow()
        }
    }
}
