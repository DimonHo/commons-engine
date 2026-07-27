plugins {
    kotlin("jvm") version "2.3.0"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    // AI 服务客户端（#74）：Resilience4j 熔断 + 重试
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.3.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // MockWebServer（#74）：无需启动 Python 服务即可端到端测试 HTTP 契约
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
