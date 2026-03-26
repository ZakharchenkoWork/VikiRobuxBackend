package com.faigenbloom.spartaculous.nutrition

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NutritionLearningTest {

    private class FakeRepo : NutritionRulesRepository {
        private val storage = mutableMapOf<String, NutritionRule>()
        override suspend fun getActiveRules(): List<NutritionRule> = storage.values
            .filter { it.enabled }
            .sortedWith(compareByDescending<NutritionRule> { it.priority }.thenBy { it.ruleId })

        override suspend fun upsertRule(rule: NutritionRule) {
            storage[rule.ruleId] = rule
        }

        override suspend fun upsertRules(rules: List<NutritionRule>) {
            for (r in rules) upsertRule(r)
        }
    }

    @Test
    fun learns_from_correction_and_applies_on_next_parse() = runBlocking {
        val text = """
            Энергетическая ценность: 200 ккал
            белки: 5 г
            углеводы: 31,2 г
            жиры: 8 г
        """.trimIndent()

        val repo = FakeRepo()
        val engine = NutritionRulesEngine(repo)
        val trainer = NutritionRuleTrainer(repo, engine)

        // 1) До обучения правил нет → парсер по rules возвращает null-значения
        val before = engine.parse(text)
        assertEquals(null, before.calories.value)
        assertEquals(null, before.proteinG.value)
        assertEquals(null, before.carbsG.value)
        assertEquals(null, before.fatG.value)

        // 2) Делаем запись скана с "плохим" parsed и вносим корректные значения в correction
        val rec = NutritionScanRecord(
            scanId = "scan_test_1",
            userId = "u1",
            createdAtEpochMs = 0L,
            ocrText = text,
            parsed = NutritionScanParser.NutritionScanDto(
                scanId = "scan_test_1",
                calories = null,
                proteinG = null,
                carbsG = null,
                fatG = null,
                sugarG = null,
                fiberG = null,
                sodiumMg = null
            ),
            appliedRules = null,
            visionFacts = null,
            correction = IngredientCorrectionDto(
                calories = 200,
                proteinG = 5.0,
                carbsG = 31.2,
                fatG = 8.0
            )
        )

        // 3) Обучаемся от правки (создаст auto_* правила и перезагрузит движок)
        trainer.trainFromCorrection(rec)

        // 4) Теперь парсер по rules должен извлечь значения и сослаться на auto_* правила
        val after = engine.parse(text)
        assertEquals(200.0, after.calories.value)
        assertTrue(after.calories.ruleId?.startsWith("auto_") == true)
        assertEquals(5.0, after.proteinG.value)
        assertTrue(after.proteinG.ruleId?.startsWith("auto_") == true)
        assertEquals(31.2, after.carbsG.value)
        assertTrue(after.carbsG.ruleId?.startsWith("auto_") == true)
        assertEquals(8.0, after.fatG.value)
        assertTrue(after.fatG.ruleId?.startsWith("auto_") == true)

        // 5) Дополнительно: интеграционный путь в DTO (должен вернуть scanId и те же значения)
        val (dto, applied) = engine.parseToDto(text)
        assertNotNull(dto.scanId)
        assertEquals(200, dto.calories)
        assertEquals(5.0, dto.proteinG)
        assertEquals(31.2, dto.carbsG)
        assertEquals(8.0, dto.fatG)
        assertTrue(applied.values.all { it.startsWith("auto_") })
    }
}
