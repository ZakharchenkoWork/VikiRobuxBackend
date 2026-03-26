package com.faigenbloom.spartaculous.food

import kotlinx.serialization.Serializable

@Serializable
data class FoodMealDto(
    val id: String,
    val name: String,
    val grams: Int,
    val calories: Int?,
    val proteins: Int?,
    val fats: Int?,
    val carbs: Int?,
    val sugar: Int?,
    // New micronutrients at meal level (always numbers, default 0 for old data)
    val sodium: Int = 0,
    val potassium: Int = 0,
    val calcium: Int = 0,
    val magnesium: Int = 0,
    val iron: Int = 0,
    val photoUrl: String? = null,
    val imageUrl: String? = null,
    val ingredientsCount: Int,
    val isSportsNutrition: Boolean,
    val recordedAtEpochMillis: Long
)

@Serializable
data class CreateMealRequest(
    val name: String,
    val grams: Int,
    val calories: Int,
    val proteins: Int = 0,
    val fats: Int = 0,
    val carbs: Int = 0,
    val sugar: Int = 0,
    val sodium: Int = 0,
    val potassium: Int = 0,
    val calcium: Int = 0,
    val magnesium: Int = 0,
    val iron: Int = 0,
    val photoUrl: String? = null,
    val imageUrl: String? = null,
    val isSportsNutrition: Boolean = false,
    val recordedAtEpochMillis: Long? = null
)

@Serializable
data class UpdateMealRequest(
    val name: String,
    val grams: Int,
    val calories: Int,
    val proteins: Int = 0,
    val fats: Int = 0,
    val carbs: Int = 0,
    val sugar: Int = 0,
    val sodium: Int = 0,
    val potassium: Int = 0,
    val calcium: Int = 0,
    val magnesium: Int = 0,
    val iron: Int = 0,
    val photoUrl: String? = null,
    val imageUrl: String? = null,
    val isSportsNutrition: Boolean = false,
    val recordedAtEpochMillis: Long
)

@Serializable
data class IngredientDto(
    val id: String,
    val mealId: String,
    val name: String,
    val grams: Int,
    val calories: Int?,
    val proteins: Int?,
    val fats: Int?,
    val carbs: Int?,
    val sugar: Int?,
    val sodium: Int,
    val potassium: Int,
    val calcium: Int,
    val magnesium: Int,
    val iron: Int,
    val photoUrl: String? = null
)

@Serializable
data class CreateIngredientRequest(
    val name: String,
    val grams: Int,
    val calories: Int,
    val proteins: Int = 0,
    val fats: Int = 0,
    val carbs: Int = 0,
    val sugar: Int = 0,
    val sodium: Int = 0,
    val potassium: Int = 0,
    val calcium: Int = 0,
    val magnesium: Int = 0,
    val iron: Int = 0,
    val photoUrl: String? = null
)

typealias UpdateIngredientRequest = CreateIngredientRequest
