package com.faigenbloom.spartaculous.nutrition

import com.faigenbloom.spartaculous.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.request.forms.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NutritionScanGeminiIT {

    private fun hasVertexEnv(): Boolean {
        // Require credentials and project variables to run this integration test
        return System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.isNotBlank() == true &&
                (System.getenv("VERTEX_PROJECT_ID")?.isNotBlank() == true || System.getenv("FIREBASE_PROJECT_ID")?.isNotBlank() == true)
    }

    @Test
    fun scan_withGemini_returnsFactsAndParsed_whenEnvConfigured() = testApplication {
        if (!hasVertexEnv()) {
            println("[SKIP] Vertex/Gemini env is not configured. Set GOOGLE_APPLICATION_CREDENTIALS and VERTEX_PROJECT_ID to run this test.")
            return@testApplication
        }

        application { module() }

        // Arrange image from project root
        val imagePath = Paths.get("scan_test.jpg")
        require(Files.exists(imagePath)) { "scan_test.jpg not found at project root" }
        val bytes = Files.readAllBytes(imagePath)

        // Act
        val response = client.submitFormWithBinaryData(
            url = "/api/nutrition/scan",
            formData = formData {
                append("image", bytes, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"image\"; filename=\"scan_test.jpg\"")
                })
            }
        ) {
            headers { append("X-User-Id", "it-user") }
        }

        val status = response.status
        val body = response.bodyAsText()
        println("/api/nutrition/scan -> ${status.value}: ${body}")

        // Assert
        assertEquals(HttpStatusCode.OK, status, "Expected 200 OK, got ${'$'}status with body: ${'$'}body")

        val root = Json.parseToJsonElement(body).jsonObject
        val facts = root["facts"]?.jsonObject
        val parsed = root["parsed"]?.jsonObject
        assertNotNull(facts, "facts should be present")
        assertNotNull(parsed, "parsed should be present")

        // At least one of macros should be present (or calories)
        val anyValue = listOf(
            facts["calories"],
            facts["proteinG"], facts["fatG"], facts["carbsG"],
            parsed["calories"], parsed["proteinG"], parsed["fatG"], parsed["carbsG"]
        ).any { it != null && it.jsonPrimitive.contentOrNull != null }
        assertTrue(anyValue, "Expected at least one nutrition value present in facts/parsed")
    }
}
