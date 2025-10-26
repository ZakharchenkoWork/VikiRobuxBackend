#!/bin/bash
set -e

# переходим в папку, где лежит build.gradle.kts
cd VikiRobuxBackend

# собираем "fat jar" (всё в одном файле)
./gradlew fatJarCustom --no-daemon || ./gradlew build --no-daemon
# запускаем сервер
java -jar build/libs/*-all.jar