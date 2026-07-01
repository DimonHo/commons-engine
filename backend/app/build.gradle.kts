plugins {
    kotlin("jvm") version "2.3.0"
    id("org.springframework.boot") version "4.1.0"
}

group = "com.commonsengine"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(project(":backend:platform-core"))
    implementation(project(":backend:matching-engine"))
    implementation(project(":backend:identity"))
    implementation(project(":backend:payment"))
    implementation(project(":backend:rating"))
    implementation(project(":backend:dispatch"))
    implementation(project(":backend:governance"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
