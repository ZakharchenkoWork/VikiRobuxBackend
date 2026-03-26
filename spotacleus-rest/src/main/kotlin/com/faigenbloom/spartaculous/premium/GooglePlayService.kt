package com.faigenbloom.spartaculous.premium

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

class GooglePlayService(
    private val credentialsFile: String?,
    private val httpTimeoutMillis: Int = (System.getenv("GOOGLE_PLAY_HTTP_TIMEOUT_MS")?.toIntOrNull() ?: 5000)
) {
    private val scope = listOf("https://www.googleapis.com/auth/androidpublisher")
    @Volatile private var credentials: GoogleCredentials? = null
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verifyPurchase(request: GooglePlayVerifyRequest): PlatformPurchaseDetails = withContext(Dispatchers.IO) {
        val pkg = request.packageName ?: throw IllegalArgumentException("packageName is required")
        val token = accessToken()
        when (request.productType.lowercase()) {
            "subs" -> fetchSubscription(request, pkg, token)
            "inapp" -> fetchInApp(request, pkg, token)
            else -> throw IllegalArgumentException("Unsupported productType '${request.productType}'")
        }
    }

    private fun ensureCredentials(): GoogleCredentials {
        val existing = credentials
        if (existing != null) return existing
        val path = credentialsFile
            ?: System.getenv("GOOGLE_PLAY_CREDENTIALS_FILE")
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        require(!path.isNullOrBlank()) { "Missing GOOGLE_PLAY_CREDENTIALS_FILE or GOOGLE_APPLICATION_CREDENTIALS" }
        val file = File(path)
        require(file.exists()) { "Google Play credentials file not found: $path" }
        val loaded = FileInputStream(file).use { GoogleCredentials.fromStream(it) }.createScoped(scope)
        credentials = loaded
        return loaded
    }

    private suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        val creds = ensureCredentials()
        val token = synchronized(creds) {
            try {
                creds.refreshIfExpired()
                val current = creds.accessToken ?: creds.refreshAccessToken()
                current.tokenValue
            } catch (ioe: IOException) {
                throw IllegalStateException("Failed to refresh Google access token: ${ioe.message}", ioe)
            }
        }
        token
    }

    private suspend fun fetchSubscription(request: GooglePlayVerifyRequest, packageName: String, accessToken: String): PlatformPurchaseDetails {
        val url = URL("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/subscriptionsv2/tokens/${request.purchaseToken}")
        val root = execGet(url, accessToken)

        val subscriptionState = root["subscriptionState"]?.jsonPrimitive?.contentOrNull
        val lineItems = root["lineItems"]?.jsonArray
        val firstItem = lineItems?.firstOrNull()?.jsonObject
        val productId = firstItem?.get("productId")?.jsonPrimitive?.contentOrNull ?: request.productId
        val expiryMillis = firstItem?.let { parseSubscriptionExpiryMillis(it) }
            ?: root["expiryTimeMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val purchaseState = when (subscriptionState) {
            "SUBSCRIPTION_STATE_ACTIVE" -> GooglePurchaseState.VALID
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> GooglePurchaseState.VALID
            "SUBSCRIPTION_STATE_ON_HOLD" -> GooglePurchaseState.PAUSED
            "SUBSCRIPTION_STATE_CANCELLED" -> GooglePurchaseState.CANCELLED
            "SUBSCRIPTION_STATE_EXPIRED" -> GooglePurchaseState.EXPIRED
            else -> GooglePurchaseState.UNKNOWN
        }
        return buildResult(request, purchaseState, productId, expiryMillis, subscriptionState)
    }

    private suspend fun fetchInApp(request: GooglePlayVerifyRequest, packageName: String, accessToken: String): PlatformPurchaseDetails {
        val url = URL("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${request.productId}/tokens/${request.purchaseToken}")
        val root = execGet(url, accessToken)
        val purchaseStateCode = root["purchaseState"]?.jsonPrimitive?.intOrNull
        val acknowledged = root["acknowledgementState"]?.jsonPrimitive?.intOrNull == 1
        val purchaseState = when (purchaseStateCode) {
            0 -> if (acknowledged) GooglePurchaseState.VALID else GooglePurchaseState.PENDING
            1 -> GooglePurchaseState.CANCELLED
            2 -> GooglePurchaseState.PENDING
            else -> GooglePurchaseState.UNKNOWN
        }
        val expiryMillis = root["expiryTimeMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        return buildResult(request, purchaseState, request.productId, expiryMillis, purchaseStateCode?.toString())
    }

    private suspend fun execGet(url: URL, accessToken: String): JsonObject = withContext(Dispatchers.IO) {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = httpTimeoutMillis
            readTimeout = httpTimeoutMillis
            addRequestProperty("Authorization", "Bearer $accessToken")
        }
        val code = conn.responseCode
        val body = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
        else conn.errorStream?.bufferedReader()?.use { it.readText() }

        if (code !in 200..299 || body == null) {
            throw UpstreamException(code = code, body = body ?: "")
        }
        return@withContext json.parseToJsonElement(body).jsonObject
    }

    private fun parseSubscriptionExpiryMillis(item: JsonObject): Long? {
        val expiryDetails = item["expiryDetails"]?.jsonObject
        val expiryTimeIso = expiryDetails?.get("expiryTime")?.jsonPrimitive?.contentOrNull
            ?: item["expiryTime"]?.jsonPrimitive?.contentOrNull
        return expiryTimeIso?.let {
            try {
                Instant.parse(it).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun buildResult(
        request: GooglePlayVerifyRequest,
        state: GooglePurchaseState,
        productId: String?,
        expiryMillis: Long?,
        rawState: String?
    ): PlatformPurchaseDetails {
        val expiryDate = expiryMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        return PlatformPurchaseDetails(
            platform = "android",
            productId = productId ?: request.productId,
            state = state,
            expiryDate = expiryDate,
            rawStatus = rawState
        )
    }

    data class PlatformPurchaseDetails(
        val platform: String,
        val productId: String,
        val state: GooglePurchaseState,
        val expiryDate: LocalDate?,
        val rawStatus: String?
    )

    enum class GooglePurchaseState { VALID, PENDING, CANCELLED, EXPIRED, PAUSED, UNKNOWN }

    data class UpstreamException(val code: Int, val body: String) : Exception()
}
