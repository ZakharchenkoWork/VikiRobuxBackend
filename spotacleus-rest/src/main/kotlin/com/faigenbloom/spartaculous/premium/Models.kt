package com.faigenbloom.spartaculous.premium

import kotlinx.serialization.Serializable

@Serializable
data class GooglePlayVerifyRequest(
    val platform: String = "android",
    val productType: String,
    val packageName: String? = null,
    val productId: String,
    val purchaseToken: String,
    val orderId: String? = null
)

@Serializable
data class PremiumVerifyResponse(
    val type: String,
    val until: String?
)
