package com.faigenbloom.models

import kotlinx.serialization.Serializable

@Serializable
data class ItemWrapper(val items: List<Item>)

@Serializable
data class Item(
    val id: String,
    val date: String,
    val description: String,
    val quantity: Int,
    val isDone: Boolean,
)

