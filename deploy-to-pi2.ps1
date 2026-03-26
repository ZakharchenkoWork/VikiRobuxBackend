$ErrorActionPreference = "Stop"

# === Настройки из старого файла ===
$PI_USER = "konstantyn"
$PI_HOST = "192.168.68.67"
$PI_PATH = "/home/konstantyn/server"
$SSH_KEY = "$env:USERPROFILE\keyProSever"
$JAR_NAME = "VikiRobuxBackend-all.jar"
$CONTAINER_NAME = "ktor"
$PORT = 8080

# Файл окружения с секретами (не коммитить в Git)
$ENV_FILE = ".env.server3"

# === Сборка проекта ===
Write-Host "Building JAR (shadowJar)..."
cmd /c "gradlew.bat buildFatJar"

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

# === Проверка и загрузка env-файла, если он существует ===
if (Test-Path $ENV_FILE) {
    Write-Host "Uploading env file..."
    scp -i "${SSH_KEY}" "${ENV_FILE}" "${PI_USER}@${PI_HOST}:${PI_PATH}/.env"
} else {
    Write-Host "Warning: Env file not found: ${ENV_FILE}. Skipping env file upload." -ForegroundColor Yellow
}

# === Копирование JAR-файла на Raspberry Pi ===
Write-Host "Uploading to Raspberry Pi..."
scp -i "${SSH_KEY}" "build/libs/${JAR_NAME}" "${PI_USER}@${PI_HOST}:${PI_PATH}/"

# === Перезапуск Docker-контейнера на Raspberry Pi ===
Write-Host "Restarting Docker container on Raspberry Pi..."

# Собираем команду docker run динамически (пробрасываем порт наружу и внутрь)
$dockerCmd = "docker run -d --restart=always -p ${PORT}:${PORT} -e PORT=${PORT} --name ${CONTAINER_NAME} -v ${PI_PATH}:/app -w /app eclipse-temurin:17-jdk java -jar ${JAR_NAME}"

# Добавляем --env-file только если файл существует
if (Test-Path $ENV_FILE) {
    $dockerCmd = "docker run -d --restart=always -p ${PORT}:${PORT} -e PORT=${PORT} --name ${CONTAINER_NAME} --env-file ${PI_PATH}/.env -v ${PI_PATH}:/app -w /app eclipse-temurin:17-jdk java -jar ${JAR_NAME}"
}

$commands = @(
    "cd ${PI_PATH}",
    "docker stop ktor || true",
    "docker rm ktor || true",
    "docker stop ktor2 || true",
    "docker rm ktor2 || true",
    "docker stop ${CONTAINER_NAME} || true",
    "docker rm ${CONTAINER_NAME} || true",
    "docker pull eclipse-temurin:17-jdk",
    $dockerCmd
)
$remoteCommand = $commands -join "; "
ssh -i "${SSH_KEY}" "${PI_USER}@${PI_HOST}" $remoteCommand

Write-Host "Done!"
