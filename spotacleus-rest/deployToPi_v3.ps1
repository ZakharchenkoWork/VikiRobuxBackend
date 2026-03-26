$ErrorActionPreference = "Stop"

# === Настройки (3-й сервер) ===
$PI_USER = "konstantyn"
$PI_HOST = "192.168.68.67"
$PI_PATH = "/home/konstantyn/server3"   # отдельная папка под третий сервер
$SSH_KEY = "$env:USERPROFILE\keyProSever"
$PORT = 8282                              # внешний порт Raspberry Pi
$CONTAINER_NAME = "ktor3"                # уникальное имя контейнера
${null} = $CONTAINER_NAME

# Локальные пути к учетным данным
# 1) Предпочитаем сервисный аккаунт: .\secrets\firebase-sa.json (не коммитить)
# 2) Фолбэк: ADC (Application Default Credentials) из gcloud
$SA_LOCAL  = Join-Path (Join-Path (Get-Location) 'secrets') 'firebase-sa.json'
$ADC_LOCAL = Join-Path $env:APPDATA 'gcloud\application_default_credentials.json'
# Google Play Service Account (локальный путь)
$GP_SA_LOCAL = Join-Path (Join-Path (Get-Location) 'secrets') 'google-play-sa.json'

# Файл окружения с секретами (не коммитить в Git)
$ENV_FILE = ".env.server3"

# === Сборка проекта ===
Write-Host "Building JAR (shadowJar)..."
cmd /c "gradlew.bat shadowJar"

# === Поиск fat JAR (*-all.jar) ===
$jar = Get-ChildItem -Path "build/libs" -Filter "*-all.jar" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    throw "Fat JAR not found in build/libs. Build may have failed."
}
$JAR_NAME = $jar.Name
Write-Host "Using JAR: $JAR_NAME"

# === Создание директории на Raspberry Pi ===
Write-Host "Ensuring target folder exists on Raspberry Pi..."
ssh -i "${SSH_KEY}" "${PI_USER}@${PI_HOST}" "mkdir -p ${PI_PATH}"

# === Загрузка учетных данных на Raspberry Pi в примонтируемую папку ===
ssh -i "${SSH_KEY}" "${PI_USER}@${PI_HOST}" "mkdir -p ${PI_PATH}/gcloud ${PI_PATH}/secrets"
if (Test-Path $SA_LOCAL) {
    Write-Host "Uploading Service Account credentials to Raspberry Pi..."
    scp -i "${SSH_KEY}" "$SA_LOCAL" "${PI_USER}@${PI_HOST}:${PI_PATH}/gcloud/application_default_credentials.json"
} elseif (Test-Path $ADC_LOCAL) {
    Write-Host "Uploading ADC to Raspberry Pi (fallback)..."
    scp -i "${SSH_KEY}" "$ADC_LOCAL" "${PI_USER}@${PI_HOST}:${PI_PATH}/gcloud/application_default_credentials.json"
} else {
    Write-Warning "No credentials found. Place SA at .\\secrets\\firebase-sa.json or login gcloud to create ADC at $ADC_LOCAL."
}

# === Загрузка Google Play SA (если есть локально) ===
if (Test-Path $GP_SA_LOCAL) {
    Write-Host "Uploading Google Play Service Account to Raspberry Pi..."
    scp -i "${SSH_KEY}" "$GP_SA_LOCAL" "${PI_USER}@${PI_HOST}:${PI_PATH}/secrets/google-play-sa.json"
} else {
    Write-Warning "Google Play SA not found: $GP_SA_LOCAL. If premium verification is needed, place google-play-sa.json under .\\secrets."
}

# === Проверка и загрузка env-файла ===
if (-not (Test-Path $ENV_FILE)) {
    throw "Env file not found: ${ENV_FILE}. Create it with MONGODB_URI and MONGODB_DB."
}
Write-Host "Uploading env file..."
scp -i "${SSH_KEY}" "${ENV_FILE}" "${PI_USER}@${PI_HOST}:${PI_PATH}/.env"

# === Копирование JAR-файла на Raspberry Pi ===
Write-Host "Uploading to Raspberry Pi..."
scp -i "${SSH_KEY}" "build/libs/${JAR_NAME}" "${PI_USER}@${PI_HOST}:${PI_PATH}/"

# === Перезапуск Docker-контейнера на Raspberry Pi ===
Write-Host "Restarting Docker container on Raspberry Pi..."

# Собираем команду docker run динамически (пробрасываем 8282 наружу и внутрь)
# Монтируем ${PI_PATH} в /app и указываем GOOGLE_APPLICATION_CREDENTIALS и GOOGLE_PLAY_CREDENTIALS_FILE внутри контейнера
$dockerCmd = "docker run -d --restart=always -p ${PORT}:${PORT} -e PORT=${PORT} -e GOOGLE_APPLICATION_CREDENTIALS=/app/gcloud/application_default_credentials.json -e GOOGLE_PLAY_CREDENTIALS_FILE=/app/secrets/google-play-sa.json --name ${CONTAINER_NAME} --env-file ${PI_PATH}/.env -v ${PI_PATH}:/app -w /app eclipse-temurin:17-jdk java -jar ${JAR_NAME}"

$commands = @(
    "cd ${PI_PATH}",
    "docker stop ktor || true",
    "docker rm ktor || true",
    "docker stop ktor2 || true",
    "docker rm ktor2 || true",
    "docker stop ${CONTAINER_NAME} || true",
    "docker rm ${CONTAINER_NAME} || true",
    "docker pull eclipse-temurin:17-jdk",
    # Пробрасываем внешний порт $PORT на внутренний $PORT (приложение слушает $PORT)
    $dockerCmd
)
$remoteCommand = $commands -join "; "
ssh -i "${SSH_KEY}" "${PI_USER}@${PI_HOST}" $remoteCommand

Write-Host "Done!"
