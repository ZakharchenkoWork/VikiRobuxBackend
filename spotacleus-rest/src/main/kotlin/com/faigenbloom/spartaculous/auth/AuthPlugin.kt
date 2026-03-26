package com.faigenbloom.spartaculous.auth

import com.faigenbloom.spartaculous.config.Config
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*

/**
 * Plugin для обработки авторизации с поддержкой тестового токена
 */
val AuthPlugin = createApplicationPlugin(name = "AuthPlugin") {
    onCall { call ->
        val authHeader = call.request.headers["Authorization"]
        
        // Если есть Authorization header с Bearer токеном
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ").trim()
            
            // Проверяем тестовый токен (только в dev режиме)
            if (Config.isDev && isTestToken(token)) {
                // Устанавливаем тестового пользователя
                call.attributes.put(UserIdKey, "test-user")
                return@onCall
            }
            
            // TODO: Здесь можно добавить настоящую JWT валидацию для production
            // val userId = validateJWT(token)
            // call.attributes.put(UserIdKey, userId)
        }
        
        // Fallback на X-User-Id заголовок (существующая логика)
        val userId = call.request.headers["X-User-Id"]
        if (userId != null) {
            call.attributes.put(UserIdKey, userId)
        }
    }
}

/**
 * Проверяет, является ли токен тестовым
 */
private fun isTestToken(token: String): Boolean {
    return token == "test-token" || 
           token == "mock-jwt-token" ||
           token.startsWith("mock-") ||
           token.startsWith("test-")
}

/**
 * Ключ для хранения userId в атрибутах запроса
 */
val UserIdKey = AttributeKey<String>("userId")

/**
 * Extension для получения userId из запроса
 */
fun ApplicationCall.getUserId(): String? {
    return attributes.getOrNull(UserIdKey)
}
