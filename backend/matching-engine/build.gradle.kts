plugins {
    id("commons-engine.spring-convention")
}

dependencies {
    implementation(project(":backend:platform-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
