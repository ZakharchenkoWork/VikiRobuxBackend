$ErrorActionPreference = "Stop"

# === Настройки ===
$PI_USER = "konstantyn"
$PI_HOST = "192.168.68.67"
$PI_PATH = "/home/konstantyn/server"
$SSH_KEY = "$env:USERPROFILE\keyProSever"
$JAR_NAME = "VikiRobuxBackend-all.jar"

# === Сборка проекта ===
Write-Host "Building JAR..."
cmd /c "gradlew.bat buildFatJar"

# === Создание директории на Raspberry Pi ===
Write-Host "Ensuring target folder exists on Raspberry Pi..."
ssh -i "${SSH_KEY}" "${PI_USER}@${PI_HOST}" "mkdir -p ${PI_PATH}"
# === Копирование JAR-файла на Raspberry Pi ===
Write-Host "Uploading to Raspberry Pi..."
scp -i "${SSH_KEY}" "build/libs/${JAR_NAME}" "${PI_USER}@${PI_HOST}:${PI_PATH}/"

# === Перезапуск Docker-контейнера на Raspberry Pi ===
Write-Host "Restarting Docker container on Raspberry Pi..."
$commands = @(
    "cd ${PI_PATH}",
    "docker stop ktor || true",
    "docker rm ktor || true",
    "docker pull eclipse-temurin:17-jdk",
    "docker run -d --restart=always -p 8080:8080 --name ktor eclipse-temurin:17-jdk java -jar ${PI_PATH}/${JAR_NAME}"
)
$remoteCommand = $commands -join "; "
ssh -i "${SSH_KEY}" "${PI_USER}@${PI_HOST}" $remoteCommands

Write-Host "Done!"
