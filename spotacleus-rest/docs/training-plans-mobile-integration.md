# Training Plans API — Спецификация

**Base URL:** `http://192.168.68.67:8282`  
**Version:** 1.0  
**Auth:** Header `X-User-Id` (обязательный для всех запросов)

---

## Эндпоинты

### POST /api/training/plans
Сохранение/обновление планов тренировок на выбранные даты (batch upsert).

#### HTTP Request
```http
POST /api/training/plans HTTP/1.1
Host: 192.168.68.67:8282
Content-Type: application/json
X-User-Id: user123

{
  "plans": [...]
}
```

#### Headers
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `Content-Type` | string | ✅ | `application/json` |
| `X-User-Id` | string | ✅ | Идентификатор пользователя |

#### Request Body Schema
```typescript
{
  plans: Array<TrainingPlanDto>  // required, min: 1
}
```

**TrainingPlanDto:**
```typescript
{
  date: string,                    // required, format: YYYY-MM-DD
  exercises: Array<PlannedExerciseDto>  // required, max: 100
}
```

**PlannedExerciseDto:**
```typescript
{
  exerciseKey: string,  // required, non-blank
  name: string,         // required, non-blank
  order: int            // required, >= 0
}
```

#### Request Example
```json
{
  "plans": [
    {
      "date": "2026-03-11",
      "exercises": [
        {
          "exerciseKey": "push_ups",
          "name": "Push-ups",
          "order": 0
        },
        {
          "exerciseKey": "squats",
          "name": "Squats",
          "order": 1
        }
      ]
    },
    {
      "date": "2026-03-12",
      "exercises": [
        {
          "exerciseKey": "running",
          "name": "Running",
          "order": 0
        }
      ]
    }
  ]
}
```

#### Response 200 OK
```typescript
{
  savedDates: Array<string>  // ISO dates that were saved/updated
}
```

**Example:**
```json
{
  "savedDates": ["2026-03-11", "2026-03-12"]
}
```

#### Response 400 Bad Request
```typescript
{
  error: string  // Human-readable error message
}
```

**Examples:**
```json
{"error": "plans array cannot be empty"}
{"error": "Invalid date format: 11-03-2026 (expected YYYY-MM-DD)"}
{"error": "Duplicate exerciseKey in plan for date 2026-03-11"}
{"error": "Too many exercises (max 100 per day)"}
{"error": "exerciseKey cannot be blank"}
{"error": "Exercise order must be >= 0"}
```

#### Response 500 Internal Server Error
```json
{
  "error": "Internal server error"
}
```

#### Business Logic
- **Upsert**: Если план на дату уже существует → полностью заменяется новым списком упражнений
- **Delete**: Если `exercises` пустой массив → план на эту дату удаляется из БД
- **Isolation**: Планы изолированы по `userId` (один пользователь не видит планы другого)
- **Validation**:
  - `date` обязательна, формат строго `YYYY-MM-DD`
  - `exerciseKey` не должен повторяться в рамках одной даты
  - Максимум 100 упражнений на одну дату
  - `order` должен быть >= 0

---

### GET /api/training/plans
Получение планов тренировок за указанный диапазон дат.

#### HTTP Request
```http
GET /api/training/plans?from=2026-03-01&to=2026-03-31 HTTP/1.1
Host: 192.168.68.67:8282
X-User-Id: user123
```

#### Headers
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `X-User-Id` | string | ✅ | Идентификатор пользователя |

#### Query Parameters
| Name | Type | Required | Format | Description |
|------|------|----------|--------|-------------|
| `from` | string | ✅ | `YYYY-MM-DD` | Начальная дата диапазона (включительно) |
| `to` | string | ✅ | `YYYY-MM-DD` | Конечная дата диапазона (включительно) |

#### Request Example
```http
GET /api/training/plans?from=2026-03-01&to=2026-03-31 HTTP/1.1
Host: 192.168.68.67:8282
X-User-Id: user123
```

#### Response 200 OK
```typescript
{
  plans: Array<TrainingPlanDto>  // Sorted by date ASC
}
```

**TrainingPlanDto:**
```typescript
{
  date: string,                    // ISO format YYYY-MM-DD
  exercises: Array<PlannedExerciseDto>  // Sorted by order ASC
}
```

**PlannedExerciseDto:**
```typescript
{
  exerciseKey: string,
  name: string,
  order: int
}
```

**Example:**
```json
{
  "plans": [
    {
      "date": "2026-03-11",
      "exercises": [
        {
          "exerciseKey": "push_ups",
          "name": "Push-ups",
          "order": 0
        },
        {
          "exerciseKey": "squats",
          "name": "Squats",
          "order": 1
        }
      ]
    },
    {
      "date": "2026-03-12",
      "exercises": [
        {
          "exerciseKey": "running",
          "name": "Running",
          "order": 0
        }
      ]
    }
  ]
}
```

**Empty result:**
```json
{
  "plans": []
}
```

#### Response 400 Bad Request
```typescript
{
  error: string
}
```

**Examples:**
```json
{"error": "from and to query parameters are required"}
{"error": "Invalid date format: 2026-3-1 (expected YYYY-MM-DD)"}
```

#### Response 500 Internal Server Error
```json
{
  "error": "Internal server error"
}
```

#### Business Logic
- Возвращает **только планы текущего пользователя** (фильтрация по `userId`)
- Упражнения в каждом плане отсортированы по полю `order` (ASC)
- Планы отсортированы по дате (ASC)
- Если планов в диапазоне нет → возвращается пустой массив `plans: []`

---

## Модели данных (DTO)

### PlannedExerciseDto
Упражнение в плане тренировки.

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `exerciseKey` | string | ✅ | non-blank | Уникальный ключ упражнения (например, `push_ups`, `squats`) |
| `name` | string | ✅ | non-blank | Отображаемое название упражнения |
| `order` | int | ✅ | >= 0 | Порядковый номер упражнения в плане (0, 1, 2, ...) |

**Kotlin:**
```kotlin
@Serializable
data class PlannedExerciseDto(
    val exerciseKey: String,
    val name: String,
    val order: Int
)

@Serializable
data class TrainingPlanDto(
    val date: String,
    val exercises: List<PlannedExerciseDto>
)
```

---

### TrainingPlanDto
План тренировки на конкретную дату.

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `date` | string | ✅ | format: `YYYY-MM-DD` | Дата плана в ISO формате |
| `exercises` | Array<PlannedExerciseDto> | ✅ | max: 100, unique `exerciseKey` | Список упражнений в плане |

**Kotlin:**
```kotlin
@Serializable
data class TrainingPlanDto(
    val date: String,
    val exercises: List<PlannedExerciseDto>
)
```

---

### SaveTrainingPlansRequest
Запрос на сохранение планов (используется в POST).

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `plans` | Array<TrainingPlanDto> | ✅ | min: 1 | Массив планов для сохранения |

**Kotlin:**
```kotlin
@Serializable
data class SaveTrainingPlansRequest(
    val plans: List<TrainingPlanDto>
)
```

---

### SaveTrainingPlansResponse
Ответ на успешное сохранение планов.

| Field | Type | Description |
|-------|------|-------------|
| `savedDates` | Array<string> | Список дат, на которые были сохранены/обновлены планы |

**Kotlin:**
```kotlin
@Serializable
data class SaveTrainingPlansResponse(
    val savedDates: List<String>
)
```

---

### GetTrainingPlansResponse
Ответ на запрос получения планов.

| Field | Type | Description |
|-------|------|-------------|
| `plans` | Array<TrainingPlanDto> | Список планов за запрошенный диапазон (отсортирован по дате) |

**Kotlin:**
```kotlin
@Serializable
data class GetTrainingPlansResponse(
    val plans: List<TrainingPlanDto>
)
```

---

## Реализация для Android

### 1. Создайте DTO модели

Файл: `data/training/TrainingPlanModels.kt`

```kotlin
package com.yourapp.data.training

import kotlinx.serialization.Serializable

@Serializable
data class PlannedExercise(
    val exerciseKey: String,
    val name: String,
    val order: Int
)

@Serializable
data class TrainingPlan(
    val date: String, // LocalDate.toString() → "2026-03-11"
    val exercises: List<PlannedExercise>
)

@Serializable
data class SaveTrainingPlansRequest(
    val plans: List<TrainingPlan>
)

@Serializable
data class SaveTrainingPlansResponse(
    val savedDates: List<String>
)

@Serializable
data class GetTrainingPlansResponse(
    val plans: List<TrainingPlan>
)
```

---

### 2. Retrofit API интерфейс

Файл: `data/training/TrainingPlansApi.kt`

```kotlin
package com.yourapp.data.training

import retrofit2.http.*

interface TrainingPlansApi {
    
    @POST("api/training/plans")
    suspend fun savePlans(
        @Header("X-User-Id") userId: String,
        @Body request: SaveTrainingPlansRequest
    ): SaveTrainingPlansResponse

    @GET("api/training/plans")
    suspend fun getPlans(
        @Header("X-User-Id") userId: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): GetTrainingPlansResponse
}
```

---

### 3. Repository слой

Файл: `data/training/TrainingPlansRepository.kt`

```kotlin
class TrainingPlansRepository(
    private val api: TrainingPlansApi,
    private val userIdProvider: () -> String
) {
    suspend fun savePlans(plans: List<TrainingPlan>): Result<List<String>> = runCatching {
        val response = api.savePlans(
            userId = userIdProvider(),
            request = SaveTrainingPlansRequest(plans)
        )
        response.savedDates
    }

    suspend fun getPlans(from: LocalDate, to: LocalDate): Result<List<TrainingPlan>> = runCatching {
        val response = api.getPlans(
            userId = userIdProvider(),
            from = from.toString(), // "2026-03-01"
            to = to.toString()
        )
        response.plans
    }
}
```

---

### 4. ViewModel (пример использования)

Файл: `ui/training/TrainingPlannerViewModel.kt`

```kotlin
class TrainingPlannerViewModel(
    private val repository: TrainingPlansRepository
) : ViewModel() {

    // Сохранить план на выбранные даты
    fun savePlan(selectedDates: List<LocalDate>, exercises: List<Exercise>) {
        viewModelScope.launch {
            val plans = selectedDates.map { date ->
                TrainingPlan(
                    date = date.toString(),
                    exercises = exercises.mapIndexed { index, ex ->
                        PlannedExercise(
                            exerciseKey = ex.key,
                            name = ex.name,
                            order = index
                        )
                    }
                )
            }
            
            repository.savePlans(plans)
                .onSuccess { savedDates ->
                    // Показать успех: "Планы сохранены на ${savedDates.size} дат"
                }
                .onFailure { error ->
                    // Показать ошибку
                }
        }
    }

    // Загрузить планы за месяц для календаря
    fun loadPlansForMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val from = yearMonth.atDay(1)
            val to = yearMonth.atEndOfMonth()
            
            repository.getPlans(from, to)
                .onSuccess { plans ->
                    // Обновить UI: показать планы в календаре
                    _plansState.value = plans.groupBy { it.date }
                }
                .onFailure { error ->
                    // Показать ошибку
                }
        }
    }

    // Удалить план на дату (отправить пустой список)
    fun deletePlan(date: LocalDate) {
        viewModelScope.launch {
            val emptyPlan = TrainingPlan(date = date.toString(), exercises = emptyList())
            repository.savePlans(listOf(emptyPlan))
                .onSuccess {
                    // План удалён
                }
        }
    }
}
```

---

## Примеры использования API

### cURL команды для тестирования

#### Сохранить план на несколько дат
```bash
curl -X POST http://192.168.68.67:8282/api/training/plans \
  -H "Content-Type: application/json" \
  -H "X-User-Id: test-user" \
  -d '{
    "plans": [
      {
        "date": "2026-03-11",
        "exercises": [
          {"exerciseKey": "push_ups", "name": "Push-ups", "order": 0},
          {"exerciseKey": "squats", "name": "Squats", "order": 1}
        ]
      },
      {
        "date": "2026-03-12",
        "exercises": [
          {"exerciseKey": "running", "name": "Running", "order": 0}
        ]
      }
    ]
  }'
```

#### Получить планы за месяц
```bash
curl -X GET "http://192.168.68.67:8282/api/training/plans?from=2026-03-01&to=2026-03-31" \
  -H "X-User-Id: test-user"
```

#### Удалить план на конкретную дату
```bash
curl -X POST http://192.168.68.67:8282/api/training/plans \
  -H "Content-Type: application/json" \
  -H "X-User-Id: test-user" \
  -d '{
    "plans": [
      {
        "date": "2026-03-11",
        "exercises": []
      }
    ]
  }'
```

---

## Сценарии использования в приложении

### Сценарий 1: Планирование тренировки на несколько дней

1. Пользователь выбирает даты в календаре: `11.03`, `13.03`, `15.03`
2. Выбирает упражнения: `Push-ups`, `Squats`, `Plank`
3. Нажимает "Сохранить"
4. Клиент отправляет:
```json
{
  "plans": [
    {
      "date": "2026-03-11",
      "exercises": [
        { "exerciseKey": "push_ups", "name": "Push-ups", "order": 0 },
        { "exerciseKey": "squats", "name": "Squats", "order": 1 },
        { "exerciseKey": "plank", "name": "Plank", "order": 2 }
      ]
    },
    {
      "date": "2026-03-13",
      "exercises": [
        { "exerciseKey": "push_ups", "name": "Push-ups", "order": 0 },
        { "exerciseKey": "squats", "name": "Squats", "order": 1 },
        { "exerciseKey": "plank", "name": "Plank", "order": 2 }
      ]
    },
    {
      "date": "2026-03-15",
      "exercises": [
        { "exerciseKey": "push_ups", "name": "Push-ups", "order": 0 },
        { "exerciseKey": "squats", "name": "Squats", "order": 1 },
        { "exerciseKey": "plank", "name": "Plank", "order": 2 }
      ]
    }
  ]
}
```

### Сценарий 2: Редактирование существующего плана

1. Пользователь открывает план на `11.03` (уже есть 3 упражнения)
2. Удаляет `Plank`, добавляет `Running`
3. Нажимает "Сохранить"
4. Клиент отправляет:
```json
{
  "plans": [
    {
      "date": "2026-03-11",
      "exercises": [
        { "exerciseKey": "push_ups", "name": "Push-ups", "order": 0 },
        { "exerciseKey": "squats", "name": "Squats", "order": 1 },
        { "exerciseKey": "running", "name": "Running", "order": 2 }
      ]
    }
  ]
}
```
→ Старый план **полностью заменяется** новым

### Сценарий 3: Удаление плана

1. Пользователь удаляет план на `11.03`
2. Клиент отправляет:
```json
{
  "plans": [
    {
      "date": "2026-03-11",
      "exercises": []
    }
  ]
}
```
→ План удаляется из БД

### Сценарий 4: Просмотр планов в календаре

1. Пользователь открывает экран календаря на март 2026
2. Клиент запрашивает: `GET /api/training/plans?from=2026-03-01&to=2026-03-31`
3. Получает список планов
4. Отображает индикаторы на датах с планами (точки, иконки и т.п.)
5. При клике на дату показывает список упражнений

---

---

## Чеклист интеграции

### Backend (готово ✅)
- [x] POST `/api/training/plans` — batch upsert планов
- [x] GET `/api/training/plans` — получение планов за диапазон
- [x] Валидация: формат даты, дубликаты, лимиты
- [x] MongoDB индексы: `(userId, date)` unique
- [x] Изоляция по `userId`
- [x] Логирование и обработка ошибок

### Android (TODO)
- [ ] Создать DTO модели (`PlannedExercise`, `TrainingPlan`, etc.)
- [ ] Добавить `TrainingPlansApi` в Retrofit
- [ ] Реализовать `TrainingPlansRepository`
- [ ] Создать `TrainingPlannerViewModel`
- [ ] UI: экран планирования с выбором дат и упражнений
- [ ] UI: календарь с индикаторами планов
- [ ] Валидация на клиенте (формат даты, дубликаты)
- [ ] Обработка ошибок (400, 500)
- [ ] Оффлайн кеширование (опционально, Room)
- [ ] Тестирование: создание, редактирование, удаление планов

---

## Рекомендации по UI/UX

### Экран планирования
- Календарь с мультивыбором дат
- Список упражнений с drag-and-drop для изменения порядка
- Кнопка "Сохранить" → отправляет все планы одним запросом
- Показывать индикатор загрузки при сохранении

### Экран календаря
- Точки/иконки на датах с планами
- Клик на дату → показать список упражнений
- Свайп для быстрого удаления плана
- Pull-to-refresh для синхронизации

### Валидация на клиенте
1. Формат даты: `LocalDate.toString()` → `"YYYY-MM-DD"`
2. Дубликаты: один `exerciseKey` не должен повторяться в рамках одной даты
3. Порядок: `order` должен быть >= 0 (обычно `0, 1, 2, ...`)
4. Лимиты: не более 100 упражнений на дату

---

## Поддержка

Вопросы по интеграции или нужны дополнительные эндпоинты — пиши!
