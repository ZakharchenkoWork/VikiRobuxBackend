# Базовый образ с JDK
FROM gradle:8.4-jdk17 AS build

WORKDIR /app
COPY . .

# Сборка fat jar (нужен shadowJar)
RUN gradle fatJarCustom --no-daemon

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Запускаем сервер
ENTRYPOINT ["java", "-jar", "app.jar"]

