plugins {
    id("commons-engine.spring-convention")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":backend:platform-core"))
    implementation(project(":backend:matching-engine"))
    implementation(project(":backend:identity"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
