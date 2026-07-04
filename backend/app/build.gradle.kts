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
    implementation(project(":backend:dispute"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.postgresql:postgresql")
    // Jackson 3.x Kotlin 模块——Spring Boot 4.x 用 Jackson 3（tools.jackson 命名空间），
    // 必须显式声明 Kotlin 模块，否则无法反序列化 Kotlin data class（POST 体全部 400/转换失败）
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    // 真实 PostgreSQL 集成测试（嵌入式，无需 docker/root）
    // 用于防止 H2 测试 profile 掩盖 Flyway/PostgreSQL 特有问题（如 ddl-auto: validate）
    testImplementation("io.zonky.test:embedded-postgres:2.0.7")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
