package com.faigenbloom.spartaculous.debug

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Временный клиент для POST https://api.aiapp.ai/api/chat.
 * Никаких зависимостей — можно удалить без последствий.
 */
class ExternalAiAppClient {
    data class Response(val status: Int, val contentType: String?, val body: String)

    fun postChat(
        jsonBody: String,
        token: String,
        userId: String,
        model: String = "29",
        platform: String = "web",
        stream: Boolean = true,
        includeCitations: Boolean = true,
        functionUse: Boolean = true,
        webSearchUse: Boolean = true,
        webSearchSource: String = "1",
        version: String = "2",
        pr: Boolean = false
    ): Response {
        val url = URL("https://api.aiapp.ai/api/chat")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 30000

            // Основные заголовки
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Origin", "https://chat.chatbot.app")
            setRequestProperty("Referer", "https://chat.chatbot.app/")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            setRequestProperty("Accept-Language", "ru,en-US;q=0.9,en;q=0.8")

            // Специфичные x-* заголовки из примера
            setRequestProperty("x_function_use", functionUse.toString())
            setRequestProperty("x_include_citations", includeCitations.toString())
            setRequestProperty("x_model", model)
            setRequestProperty("x_platform", platform)
            setRequestProperty("x_pr", pr.toString())
            setRequestProperty("x_stream", stream.toString())
            setRequestProperty("x_token", token)
            setRequestProperty("x_user_id", userId)
            setRequestProperty("x_version", version)
            setRequestProperty("x_web_search_source", webSearchSource)
            setRequestProperty("x_web_search_use", webSearchUse.toString())
        }

        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write(jsonBody)
        }

        val code = conn.responseCode
        val ctype = conn.getHeaderField("Content-Type")
        val reader = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val body = reader?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: ""
        conn.disconnect()
        return Response(status = code, contentType = ctype, body = body)
    }
}
