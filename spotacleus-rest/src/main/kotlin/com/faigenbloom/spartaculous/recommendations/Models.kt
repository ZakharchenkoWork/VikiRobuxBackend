package com.faigenbloom.spartaculous.recommendations

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationListItemDto(
    val id: String,
    val title: String,
    val preview: String,
    val imageUrl: String? = null,
    val publishedAt: String
)

@Serializable
data class RecommendationListResponse(
    val items: List<RecommendationListItemDto>,
    val nextCursor: String? = null
)

@Serializable
data class RecommendationDetailDto(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    val body: String
)
