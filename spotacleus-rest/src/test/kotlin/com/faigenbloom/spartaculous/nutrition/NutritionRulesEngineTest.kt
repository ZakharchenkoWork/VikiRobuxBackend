package com.faigenbloom.spartaculous.nutrition

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NutritionRulesEngineTest {

    private class FakeRulesRepo(private val rules: List<NutritionRule>) : NutritionRulesRepository {
        override suspend fun getActiveRules(): List<NutritionRule> = rules
    }

    @Test
    fun ru_label_is_parsed_by_rules() = runBlocking {
        val text = """
            Энергетическая ценность: 200 ккал
            белки: 5 г
            углеводы: 31,2 г
            жиры: 8 г
        """.trimIndent()

        val rules = listOf(
            NutritionRule(
                ruleId = "cal_ru_t",
                field = "calories",
                includePatterns = listOf("(?i)(?:ккал|kcal)\\D{0,10}(\\d{1,4})", "(?i)(\\d{1,4})\\s*(?:ккал|kcal)", "(?i)энерг[а-я]*\\D{0,10}(\\d{1,4})"),
                contextTokens = listOf("ккал","энерг"),
                toInt = true,
                priority = 200
            ),
            NutritionRule(
                ruleId = "prot_ru_t",
                field = "proteinG",
                includePatterns = listOf("(?i)бел[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)"),
                contextTokens = listOf("бел"),
                priority = 150
            ),
            NutritionRule(
                ruleId = "carb_ru_t",
                field = "carbsG",
                includePatterns = listOf("(?i)углев[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)"),
                contextTokens = listOf("углев"),
                priority = 150
            ),
            NutritionRule(
                ruleId = "fat_ru_t",
                field = "fatG",
                includePatterns = listOf("(?i)жир[\\p{L}]*\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|г)"),
                contextTokens = listOf("жир"),
                priority = 150
            )
        )
        val engine = NutritionRulesEngine(FakeRulesRepo(rules))
        val (dto, applied) = engine.parseToDto(text)

        assertNotNull(dto.scanId)
        assertEquals(200, dto.calories)
        assertEquals(5.0, dto.proteinG)
        assertEquals(31.2, dto.carbsG)
        assertEquals(8.0, dto.fatG)
        assertTrue(applied["calories"]?.startsWith("cal_ru_") == true)
        assertTrue(applied["proteinG"]?.startsWith("prot_ru_") == true)
        assertTrue(applied["carbsG"]?.startsWith("carb_ru_") == true)
        assertTrue(applied["fatG"]?.startsWith("fat_ru_") == true)
    }

    @Test
    fun fallback_parser_used_when_no_rules_match() = runBlocking {
        val text = """
            Calories: 210 kcal
            Protein 6 g
            Carbs 30 g
            Fat 8 g
        """.trimIndent()
        val engine = NutritionRulesEngine(FakeRulesRepo(emptyList()))
        val (dto, applied) = engine.parseToDto(text)
        // We expect something non-null due to legacy fallback
        assertNotNull(dto.scanId)
        // calories likely parsed by fallback
        assertNotNull(dto.calories)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun android_fixture_values_parsed_by_fallback() = runBlocking {
        // From Android UI test stub: 200 kcal, 5.0 protein, 31.2 carbs, 8.0 fat
        val text = """
            Calories: 200 kcal
            Protein: 5 g
            Carbohydrates: 31.2 g
            Fat: 8 g
        """.trimIndent()
        val engine = NutritionRulesEngine(FakeRulesRepo(emptyList()))
        val (dto, applied) = engine.parseToDto(text)
        assertNotNull(dto.scanId)
        assertEquals(200, dto.calories)
        assertEquals(5.0, dto.proteinG)
        assertEquals(31.2, dto.carbsG)
        assertEquals(8.0, dto.fatG)
        assertTrue(applied.isEmpty())
    }
}
