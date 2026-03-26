package com.faigenbloom.spartaculous.ai

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class GeminiService(
    private val projectId: String? = (System.getenv("VERTEX_PROJECT_ID") ?: System.getenv("FIREBASE_PROJECT_ID")),
    private val location: String = System.getenv("VERTEX_LOCATION") ?: "us-central1",
    private val model: String = System.getenv("GEMINI_MODEL") ?: "gemini-1.5-flash",
    private val httpTimeoutMillis: Int = (System.getenv("GEMINI_HTTP_TIMEOUT_MS")?.toIntOrNull() ?: 15000),
    private val apiKey: String? = System.getenv("GEMINI_API_KEY")
) {
    private val scope = listOf("https://www.googleapis.com/auth/cloud-platform")
    @Volatile private var credentials: GoogleCredentials? = null
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class NutritionDto(
        val calories: Int? = null,
        val protein_g: Double? = null,
        val fat_g: Double? = null,
        val carbs_g: Double? = null,
        val sugar_g: Double? = null,
        val fiber_g: Double? = null,
        val sodium_mg: Int? = null,
        val serving_size: String? = null,
        val confidence: Double? = null
    )

    suspend fun analyzeNutrition(imageBytes: ByteArray, promptExtra: String? = null): NutritionDto = withContext(Dispatchers.IO) {
        require(!projectId.isNullOrBlank()) { "VERTEX_PROJECT_ID or FIREBASE_PROJECT_ID is not set" }
        val (url, bearer) = if (!apiKey.isNullOrBlank()) {
            // Prefer explicit location to avoid backend defaulting to unsupported region (e.g., europe-west1)
            val apiLoc = System.getenv("GEMINI_API_LOCATION")?.ifBlank { null } ?: location
            val keyPath = "https://${apiLoc}-aiplatform.googleapis.com/v1/projects/${projectId}/locations/${apiLoc}/publishers/google/models/${model}:generateContent?key=${apiKey}"
            URL(keyPath) to null
        } else {
            val oauthPath = "https://${location}-aiplatform.googleapis.com/v1/projects/${projectId}/locations/${location}/publishers/google/models/${model}:generateContent"
            URL(oauthPath) to accessToken()
        }

        val basePrompt = buildString {
            append("You are an expert nutrition analyzer. Given a photo of a food product or nutrition label, return STRICT JSON only. ")
            append("Keys (all lowercase): calories (int, per 100g or per 100ml), protein_g (number), fat_g (number), carbs_g (number), sugar_g (number or null), fiber_g (number or null), sodium_mg (int or null), serving_size (string like 'per 100g'/'per 100ml'), confidence (0..1). ")
            append("If uncertain or value absent, use null and set confidence <= 0.5. Do not include any extra text, units only as implied by keys.")
        }
        val prompt = if (promptExtra.isNullOrBlank()) basePrompt else "$basePrompt Additional context: $promptExtra"

        val body = buildRequestBody(prompt, imageBytes)
        val responseText = execPost(url, bearer, body)
        parseNutrition(responseText)
    }

    private fun ensureCredentials(): GoogleCredentials {
        val existing = credentials
        if (existing != null) return existing
        val path = System.getenv("GOOGLE_PLAY_CREDENTIALS_FILE")
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        require(!path.isNullOrBlank()) { "Missing GOOGLE_APPLICATION_CREDENTIALS or GOOGLE_PLAY_CREDENTIALS_FILE" }
        val file = File(path)
        require(file.exists()) { "Credentials file not found: $path" }
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

    private fun buildRequestBody(prompt: String, imageBytes: ByteArray): String {
        val b64 = Base64.getEncoder().encodeToString(imageBytes)
        val obj = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(prompt)) })
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", JsonPrimitive("image/jpeg"))
                                put("data", JsonPrimitive(b64))
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(0.2))
                put("maxOutputTokens", JsonPrimitive(512))
            })
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private suspend fun execPost(url: URL, accessToken: String?, body: String): String = withContext(Dispatchers.IO) {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = httpTimeoutMillis
            readTimeout = httpTimeoutMillis
            doOutput = true
            if (!accessToken.isNullOrBlank()) {
                addRequestProperty("Authorization", "Bearer $accessToken")
            }
            addRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
        else conn.errorStream?.bufferedReader()?.use { it.readText() }
        if (code !in 200..299 || resp == null) throw UpstreamException(code, resp ?: "")
        resp
    }

    private fun parseNutrition(responseText: String): NutritionDto {
        val root = json.parseToJsonElement(responseText).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: throw IllegalStateException("No candidates in Vertex response")
        val first = candidates.firstOrNull()?.jsonObject ?: throw IllegalStateException("Empty candidates in Vertex response")
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray
            ?: first["content"]?.jsonArray
            ?: throw IllegalStateException("No parts in Vertex response")
        val textPart = parts.firstNotNullOfOrNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?: throw IllegalStateException("No text part in Vertex response")
        val cleaned = textPart.trim().removePrefix("```").removeSuffix("```").trim()
        return try {
            json.decodeFromString(NutritionDto.serializer(), cleaned)
        } catch (_: Throwable) {
            // Try to extract JSON object substring
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) {
                json.decodeFromString(NutritionDto.serializer(), cleaned.substring(start, end + 1))
            } else {
                throw IllegalStateException("Model did not return strict JSON: $cleaned")
            }
        }
    }

    data class UpstreamException(val code: Int, val body: String) : Exception()
}
