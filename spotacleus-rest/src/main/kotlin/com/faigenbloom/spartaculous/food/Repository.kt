package com.faigenbloom.spartaculous.food

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

interface FoodRepository {
    fun clearUser(userId: String)

    fun listMeals(userId: String): List<FoodMealDto>
    fun addMeal(userId: String, req: CreateMealRequest): FoodMealDto
    fun updateMeal(userId: String, mealId: String, req: UpdateMealRequest): FoodMealDto
    fun deleteMeal(userId: String, mealId: String)

    fun listIngredients(userId: String, mealId: String): List<IngredientDto>
    fun addIngredient(userId: String, mealId: String, req: CreateIngredientRequest): IngredientDto
    fun updateIngredient(userId: String, mealId: String, ingredientId: String, req: UpdateIngredientRequest): IngredientDto
    fun deleteIngredient(userId: String, mealId: String, ingredientId: String)
}

class InMemoryFoodRepository : FoodRepository {
    // userId -> (mealId -> meal)
    private val meals = ConcurrentHashMap<String, ConcurrentHashMap<String, FoodMealDto>>()
    // userId -> (mealId -> (ingredientId -> ingredient))
    private val ingredients = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, IngredientDto>>>()

    override fun clearUser(userId: String) {
        meals.remove(userId)
        ingredients.remove(userId)
    }

    override fun listMeals(userId: String): List<FoodMealDto> {
        val userMeals = meals[userId] ?: return emptyList()
        val userIngredients = ingredients[userId]
        return userMeals.values
            .map { meal ->
                val count = userIngredients?.get(meal.id)?.size ?: 0
                meal.copy(ingredientsCount = count)
            }
            .sortedByDescending { it.recordedAtEpochMillis }
    }

    override fun addMeal(userId: String, req: CreateMealRequest): FoodMealDto {
        val id = UUID.randomUUID().toString()
        val recorded = req.recordedAtEpochMillis ?: Instant.now().toEpochMilli()
        val meal = FoodMealDto(
            id = id,
            name = req.name.trim(),
            grams = req.grams,
            calories = req.calories.takeIf { it != 0 },
            proteins = req.proteins.takeIf { it != 0 },
            fats = req.fats.takeIf { it != 0 },
            carbs = req.carbs.takeIf { it != 0 },
            sugar = req.sugar.takeIf { it != 0 },
            sodium = req.sodium,
            potassium = req.potassium,
            calcium = req.calcium,
            magnesium = req.magnesium,
            iron = req.iron,
            photoUrl = req.photoUrl?.takeIf { it.isNotBlank() },
            imageUrl = req.imageUrl?.takeIf { it.isNotBlank() },
            ingredientsCount = 0,
            isSportsNutrition = req.isSportsNutrition,
            recordedAtEpochMillis = recorded
        )
        meals.computeIfAbsent(userId) { ConcurrentHashMap() }[id] = meal
        // ensure meal ingredients map exists
        ingredients.computeIfAbsent(userId) { ConcurrentHashMap() }.computeIfAbsent(id) { ConcurrentHashMap() }
        return meal
    }

    override fun updateMeal(userId: String, mealId: String, req: UpdateMealRequest): FoodMealDto {
        val userMeals = meals[userId] ?: throw IllegalArgumentException("Meal not found")
        val existing = userMeals[mealId] ?: throw IllegalArgumentException("Meal not found")
        val count = ingredients[userId]?.get(mealId)?.size ?: 0
        val updated = existing.copy(
            name = req.name.trim(),
            grams = req.grams,
            calories = req.calories.takeIf { it != 0 },
            proteins = req.proteins.takeIf { it != 0 },
            fats = req.fats.takeIf { it != 0 },
            carbs = req.carbs.takeIf { it != 0 },
            sugar = req.sugar.takeIf { it != 0 },
            sodium = req.sodium,
            potassium = req.potassium,
            calcium = req.calcium,
            magnesium = req.magnesium,
            iron = req.iron,
            photoUrl = req.photoUrl?.takeIf { it.isNotBlank() },
            imageUrl = req.imageUrl?.takeIf { it.isNotBlank() } ?: existing.imageUrl,
            isSportsNutrition = req.isSportsNutrition,
            recordedAtEpochMillis = req.recordedAtEpochMillis,
            ingredientsCount = count
        )
        userMeals[mealId] = updated
        return updated
    }

    override fun deleteMeal(userId: String, mealId: String) {
        meals[userId]?.remove(mealId) ?: throw IllegalArgumentException("Meal not found")
        ingredients[userId]?.remove(mealId)
    }

    override fun listIngredients(userId: String, mealId: String): List<IngredientDto> {
        val map = ingredients[userId]?.get(mealId) ?: return emptyList()
        return map.values.toList()
    }

    override fun addIngredient(userId: String, mealId: String, req: CreateIngredientRequest): IngredientDto {
        // validate meal exists
        if (meals[userId]?.containsKey(mealId) != true) throw IllegalArgumentException("Meal not found")
        val id = UUID.randomUUID().toString()
        val ing = IngredientDto(
            id = id,
            mealId = mealId,
            name = req.name.trim(),
            grams = req.grams,
            calories = req.calories.takeIf { it != 0 },
            proteins = req.proteins.takeIf { it != 0 },
            fats = req.fats.takeIf { it != 0 },
            carbs = req.carbs.takeIf { it != 0 },
            sugar = req.sugar.takeIf { it != 0 },
            sodium = req.sodium,
            potassium = req.potassium,
            calcium = req.calcium,
            magnesium = req.magnesium,
            iron = req.iron,
            photoUrl = req.photoUrl?.takeIf { it.isNotBlank() }
        )
        val perMeal = ingredients
            .computeIfAbsent(userId) { ConcurrentHashMap() }
            .computeIfAbsent(mealId) { ConcurrentHashMap() }
        perMeal[id] = ing
        // optionally bump count in meal snapshot
        meals[userId]?.computeIfPresent(mealId) { _, m -> m.copy(ingredientsCount = perMeal.size) }
        return ing
    }

    override fun updateIngredient(userId: String, mealId: String, ingredientId: String, req: UpdateIngredientRequest): IngredientDto {
        val perMeal = ingredients[userId]?.get(mealId) ?: throw IllegalArgumentException("Meal not found")
        val existing = perMeal[ingredientId] ?: throw IllegalArgumentException("Ingredient not found")
        val updated = existing.copy(
            name = req.name.trim(),
            grams = req.grams,
            calories = req.calories.takeIf { it != 0 },
            proteins = req.proteins.takeIf { it != 0 },
            fats = req.fats.takeIf { it != 0 },
            carbs = req.carbs.takeIf { it != 0 },
            sugar = req.sugar.takeIf { it != 0 },
            sodium = req.sodium,
            potassium = req.potassium,
            calcium = req.calcium,
            magnesium = req.magnesium,
            iron = req.iron,
            photoUrl = req.photoUrl?.takeIf { it.isNotBlank() }
        )
        perMeal[ingredientId] = updated
        return updated
    }

    override fun deleteIngredient(userId: String, mealId: String, ingredientId: String) {
        val perMeal = ingredients[userId]?.get(mealId) ?: throw IllegalArgumentException("Meal not found")
        perMeal.remove(ingredientId) ?: throw IllegalArgumentException("Ingredient not found")
        meals[userId]?.computeIfPresent(mealId) { _, m -> m.copy(ingredientsCount = perMeal.size) }
    }

    fun knownUserIds(): Set<String> {
        val ids = mutableSetOf<String>()
        ids += meals.keys
        ids += ingredients.keys
        return ids
    }

    fun seedTodayMeals(userId: String, count: Int, baseEpochMillis: Long = Instant.now().toEpochMilli()): Int {
        if (count <= 0) return 0
        val baseDayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        var inserted = 0
        repeat(count) { idx ->
            val recorded = baseEpochMillis - idx * 45L * 60L * 1000L
            val mealName = sampleMealName(idx)
            val req = CreateMealRequest(
                name = mealName,
                grams = 200 + (idx % 3) * 50,
                calories = 300 + (idx % 4) * 50,
                proteins = 20 + (idx % 5) * 3,
                fats = 10 + (idx % 4) * 2,
                carbs = 40 + (idx % 3) * 10,
                sugar = 5 + (idx % 2) * 3,
                sodium = 200 + (idx % 5) * 50,
                potassium = 250 + (idx % 5) * 40,
                calcium = 60 + (idx % 4) * 10,
                magnesium = 40 + (idx % 3) * 8,
                iron = 8 + (idx % 3) * 2,
                recordedAtEpochMillis = recorded.coerceAtLeast(baseDayStart)
            )
            addMeal(userId, req)
            inserted++
        }
        return inserted
    }

    private fun sampleMealName(idx: Int): String {
        val options = listOf(
            "Protein Bowl",
            "Grilled Chicken",
            "Pasta Lunch",
            "Veggie Omelette",
            "Yogurt Parfait",
            "Salmon Salad",
            "Rice with Beef",
            "Tuna Sandwich",
            "Tofu Stir-fry",
            "Smoothie"
        )
        return options[idx % options.size] + " #${idx + 1}"
    }
}
