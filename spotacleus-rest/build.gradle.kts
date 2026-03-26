import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val ktor_version = "2.3.7"
val kotlin_version = "1.9.22"
val logback_version = "1.4.14"
val koin_version = "3.5.0"
val kmongo_version = "4.11.0"

plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.faigenbloom.famillyBudget"
version = "0.0.1"

application {
    mainClass.set("com.faigenbloom.spartaculous.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers:$ktor_version")
    
    // MongoDB
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:$kmongo_version")
    implementation("org.litote.kmongo:kmongo-id-serialization:$kmongo_version")
    
    // Koin
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")
    
    // Firebase Admin (for verifying ID tokens)
    implementation("com.google.firebase:firebase-admin:9.2.0")
    
    // JWT (keyless verification using Google's public certs)
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.0")
    
    // Testing
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xopt-in=kotlin.RequiresOptIn")
    }
}

// For JVM target
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Fat JAR via Shadow
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("SpartaculousRest")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to "com.faigenbloom.spartaculous.ApplicationKt"))
    }
}
