val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.0.20"
    id("io.ktor.plugin") version "3.0.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

group = "com.faigenbloom"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}
tasks.register<Jar>("fatJarCustom") {
    group = "build"
    archiveFileName.set("app-all.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.faigenbloom.ApplicationKt"
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.named("jar").get() as CopySpec)
}
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.0.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.1")

    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.1")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.10.1")
    implementation("ch.qos.logback:logback-classic:1.5.6")

}
