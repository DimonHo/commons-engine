plugins {
    id("commons-engine.spring-convention")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
