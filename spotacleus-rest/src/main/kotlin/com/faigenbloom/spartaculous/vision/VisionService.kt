package com.faigenbloom.spartaculous.vision

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class VisionService {
    private fun loadCredentials(): GoogleCredentials {
        val json = System.getenv("FIREBASE_CREDENTIALS")
        if (!json.isNullOrBlank()) {
            return GoogleCredentials.fromStream(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
        }
        val path = System.getenv("FIREBASE_CREDENTIALS_FILE")
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        if (!path.isNullOrBlank()) {
            val f = File(path)
            require(f.exists()) { "Credentials file not found: $path" }
            return GoogleCredentials.fromStream(f.inputStream())
        }
        return GoogleCredentials.getApplicationDefault()
    }

    private fun getAccessToken(): String {
        val creds = loadCredentials().createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        creds.refreshIfExpired()
        return creds.accessToken.tokenValue
    }

    @Serializable
    data class OcrResult(
        val text: String,
    )

    fun extractText(imageBytes: ByteArray): OcrResult {
        val token = getAccessToken()
        val endpoint = URL("https://vision.googleapis.com/v1/images:annotate")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val b64 = Base64.getEncoder().encodeToString(imageBytes)
        val payload = """
            {"requests":[{"image":{"content":"$b64"},"features":[{"type":"DOCUMENT_TEXT_DETECTION"}]}]}
        """.trimIndent()

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val body = try {
            if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
            else conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
        if (code !in 200..299) {
            throw IllegalStateException("Vision API error HTTP $code: $body")
        }
        val root = Json.parseToJsonElement(body).jsonObject
        val responses = root["responses"]?.jsonArray
        val first = responses?.firstOrNull()?.jsonObject
        val text = first?.get("fullTextAnnotation")?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        return OcrResult(text = text)
    }

    @Serializable
    data class NutritionFacts(
        val calories: Int? = null,
        val proteinG: Double? = null,
        val carbsG: Double? = null,
        val fatG: Double? = null,
        val sugarG: Double? = null,
        val fiberG: Double? = null,
        val sodiumMg: Int? = null,
    )

    fun parseNutritionFacts(text: String): NutritionFacts {
        fun findInt(regex: Regex): Int? = regex.find(text)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.toInt()
        fun findDouble(regex: Regex): Double? = regex.find(text)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        val t = text.lowercase()
        return NutritionFacts(
            calories = findInt(Regex("(?im)\\bcalories?\\b[:\\s]*([0-9]{1,4})")),
            proteinG = findDouble(Regex("(?im)\\bprotein\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g")),
            carbsG = findDouble(Regex("(?im)\\b(carbohydrates?|carbs?)\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g")),
            fatG = findDouble(Regex("(?im)\\bfat\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g")),
            sugarG = findDouble(Regex("(?im)\\bsugars?\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g")),
            fiberG = findDouble(Regex("(?im)\\bfiber\\b[^0-9]*([0-9]+(?:[.,][0-9]+)?)\\s*g")),
            sodiumMg = findInt(Regex("(?im)\\bsodium\\b[^0-9]*([0-9]+)\\s*mg"))
        )
    }
}
