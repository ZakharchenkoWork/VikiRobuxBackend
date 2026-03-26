# 🚀 Quick Start для фронтенд-команды

## Тестовые токены теперь работают! ✅

### Используйте в UI тестах:

```kotlin
headers["Authorization"] = "Bearer test-token"
```

### Поддерживаемые токены:
- ✅ `test-token` (рекомендуем)
- ✅ `mock-jwt-token`
- ✅ `test-*` (любой с префиксом `test-`)
- ✅ `mock-*` (любой с префиксом `mock-`)

### Что это даёт:
- Автоматическая авторизация как `test-user`
- Работает на всех эндпоинтах (Goals, Settings, и т.д.)
- Только в dev режиме (production безопасен)

### Пример:

```kotlin
// До
val response = client.get("/api/goals/overview") {
    header("X-User-Id", "test-user")
}

// Теперь можно так
val response = client.get("/api/goals/overview") {
    header("Authorization", "Bearer test-token")
}
```

### Проверка:

```bash
curl -H "Authorization: Bearer test-token" \
  http://192.168.68.67:8282/api/goals/overview
```

Должно вернуть 200 OK с данными.

---

**Готово к использованию!** 🎉

Полная документация: [TEST_TOKENS_DOCUMENTATION.md](./TEST_TOKENS_DOCUMENTATION.md)
