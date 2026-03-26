package com.faigenbloom.spartaculous.nutrition

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class InMemoryNutritionRulesRepository : NutritionRulesRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getActiveRules(): List<NutritionRule> {
        // Priority: file -> env JSON -> defaults
        val filePath = System.getenv("NUTRITION_RULES_FILE")?.trim().orEmpty()
        if (filePath.isNotBlank()) {
            runCatching {
                val text = File(filePath).readText(Charsets.UTF_8)
                val rules = json.decodeFromString<List<NutritionRule>>(text)
                return rules.filter { it.enabled }.sortedWith(compareByDescending<NutritionRule> { it.priority }.thenBy { it.ruleId })
            }
        }
        val envJson = System.getenv("NUTRITION_RULES_JSON")?.trim().orEmpty()
        if (envJson.isNotBlank()) {
            runCatching {
                val rules = json.decodeFromString<List<NutritionRule>>(envJson)
                return rules.filter { it.enabled }.sortedWith(compareByDescending<NutritionRule> { it.priority }.thenBy { it.ruleId })
            }
        }
        // Defaults (stable minimal rules RU/EN)
        return defaultRules().sortedWith(compareByDescending<NutritionRule> { it.priority }.thenBy { it.ruleId })
    }

    private fun defaultRules(): List<NutritionRule> = listOf(
        // Calories
        NutritionRule(
            ruleId = "cal_default_1",
            field = "calories",
            locales = emptyList(),
            includePatterns = listOf(
                "(?i)(?:ккал|kcal)\\D{0,10}(\\d{1,4})",
                "(?i)(\\d{1,4})\\s*(?:ккал|kcal)",
                "(?i)calories\\s*[:=\\-]?\\s*(\\d{1,4})",
                "(?i)энерг[а-я]*\\D{0,10}(\\d{1,4})"
            ),
            contextTokens = listOf("ккал","kcal","calorie","calories","энерг","energy"),
            toInt = true,
            priority = 200
        ),
        // Protein
        NutritionRule(
            ruleId = "prot_default_1",
            field = "proteinG",
            includePatterns = listOf(
                "(?i)бел[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)",
                "(?i)protein\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g"
            ),
            contextTokens = listOf("бел","protein"),
            priority = 150
        ),
        // Carbs
        NutritionRule(
            ruleId = "carb_default_1",
            field = "carbsG",
            includePatterns = listOf(
                "(?i)углев[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)",
                "(?i)(carbohydrates?|carbs?)\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g"
            ),
            contextTokens = listOf("углев","carb","carbohydrate"),
            priority = 150
        ),
        // Fat
        NutritionRule(
            ruleId = "fat_default_1",
            field = "fatG",
            includePatterns = listOf(
                "(?i)жир[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)",
                "(?i)fat\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g"
            ),
            contextTokens = listOf("жир","fat"),
            priority = 150
        ),
        // Sugar
        NutritionRule(
            ruleId = "sugar_default_1",
            field = "sugarG",
            includePatterns = listOf(
                "(?i)сахар\\w*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)",
                "(?i)sugars?\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g"
            ),
            contextTokens = listOf("сахар","sugar","sugars"),
            priority = 140
        ),
        // Fiber
        NutritionRule(
            ruleId = "fiber_default_1",
            field = "fiberG",
            includePatterns = listOf(
                "(?i)(?:клетчатка|пищ[её]вые\\s+волокна)\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)",
                "(?i)fiber\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g"
            ),
            contextTokens = listOf("клетчат","волокна","fiber"),
            priority = 130
        ),
        // Sodium (mg)
        NutritionRule(
            ruleId = "sodium_default_1",
            field = "sodiumMg",
            includePatterns = listOf(
                "(?i)(?:натрий|натрия)\\s*:?\\s*(\\d{1,5})\\s*(?:mg|мг)",
                "(?i)sodium\\b[^0-9]*([0-9]+)\\s*mg"
            ),
            contextTokens = listOf("натрий","sodium"),
            toInt = true,
            priority = 120
        )
    )
}
