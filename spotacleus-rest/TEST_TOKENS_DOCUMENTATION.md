# Тестовые токены для UI тестов

## ✅ Реализовано

Backend теперь поддерживает **тестовые токены** для UI/автоматизированных тестов.

## 🔑 Поддерживаемые токены

В **dev режиме** (когда `Config.isDev = true`) backend принимает следующие токены:

1. **`test-token`** — основной тестовый токен
2. **`mock-jwt-token`** — альтернативный тестовый токен
3. **`test-*`** — любой токен, начинающийся с `test-` (например, `test-12345`, `test-user-abc`)
4. **`mock-*`** — любой токен, начинающийся с `mock-` (например, `mock-abcdef`, `mock-session-123`)

## 📝 Использование

### В UI тестах (Kotlin/Android)

```kotlin
// Вариант 1: Простой тестовый токен
val testToken = "test-token"
val headers = mapOf("Authorization" to "Bearer $testToken")

// Вариант 2: Динамический токен для разных тестов
val testToken = "test-${testName}"
val headers = mapOf("Authorization" to "Bearer $testToken")

// Пример запроса
val response = client.get("http://192.168.68.67:8282/api/goals/overview") {
    header("Authorization", "Bearer test-token")
}
```

### В HTTP клиентах

```bash
# cURL
curl -X GET "http://192.168.68.67:8282/api/goals/overview" \
  -H "Authorization: Bearer test-token"

# HTTPie
http GET http://192.168.68.67:8282/api/goals/overview \
  Authorization:"Bearer test-token"
```

### В PowerShell

```powershell
Invoke-WebRequest -Uri "http://192.168.68.67:8282/api/goals/overview" `
    -Headers @{"Authorization" = "Bearer test-token"} `
    -Method GET
```

## 🎯 Поведение

Когда backend получает тестовый токен:
- ✅ Пропускает JWT валидацию
- ✅ Автоматически использует `userId = "test-user"`
- ✅ Возвращает данные как для обычного авторизованного пользователя
- ⚠️ **Работает ТОЛЬКО в dev режиме** (production не поддерживает)

## 📋 Поддерживаемые эндпоинты

Все эндпоинты, требующие авторизацию, теперь поддерживают тестовые токены:

### Goals API
- `GET /api/goals/overview`
- `PUT /api/goals/weight`
- `PUT /api/goals/calories`
- `PUT /api/goals/bodyfat`
- `GET /api/goals/training`
- `PUT /api/goals/training`
- `GET /api/goals/recovery`
- `PUT /api/goals/recovery`
- `GET /api/goals/calories/logs`
- `POST /api/goals/calories/logs`
- `GET /api/goals/recovery/logs`
- `POST /api/goals/recovery/logs`
- `GET /api/goals/macros`
- `PUT /api/goals/macros`
- `GET /api/goals/plan`
- `PUT /api/goals/plan`
- `GET /api/goals/templates`
- `POST /api/goals/templates`
- `DELETE /api/goals/templates/{id}`
- `GET /api/goals/analytics`

### Settings API
- `GET /api/settings/profile`
- `PUT /api/settings/profile`
- `GET /api/settings/preferences`
- `PUT /api/settings/preferences`
- `GET /api/settings/premium/status`

### Другие API
Все остальные эндпоинты, использующие `X-User-Id` заголовок, также поддерживают тестовые токены через `Authorization: Bearer`.

## 🔒 Безопасность

- ✅ Тестовые токены работают **только в dev режиме**
- ✅ В production тестовые токены **игнорируются** и возвращается 401
- ✅ Проверка режима: `Config.isDev`
- ✅ Fallback на `X-User-Id` заголовок сохранён для обратной совместимости

## 🧪 Тестирование

Запустите тестовый скрипт для проверки:

```powershell
.\test-auth-tokens.ps1
```

Скрипт проверит все поддерживаемые токены на всех основных эндпоинтах.

## 📞 Для фронтенд-команды

**Рекомендуемый токен для UI тестов:** `test-token`

```kotlin
// В ваших UI тестах
companion object {
    const val TEST_AUTH_TOKEN = "test-token"
}

// Использование
headers["Authorization"] = "Bearer $TEST_AUTH_TOKEN"
```

## ❓ FAQ

**Q: Какой токен использовать?**  
A: Рекомендуем `test-token` — он простой и понятный.

**Q: Можно ли использовать свой токен?**  
A: Да, любой токен начинающийся с `test-` или `mock-` будет работать.

**Q: Работает ли в production?**  
A: Нет, только в dev режиме. В production вернётся 401.

**Q: Какой userId используется?**  
A: Всегда `test-user` для всех тестовых токенов.

**Q: Нужно ли менять существующие тесты с X-User-Id?**  
A: Нет, `X-User-Id` продолжает работать. Тестовые токены — это дополнительная опция.

---

**Дата обновления:** 2026-03-11  
**Версия:** 1.0  
**Статус:** ✅ Deployed
