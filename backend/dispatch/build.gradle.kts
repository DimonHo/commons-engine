plugins {
    id("commons-engine-convention")
}

dependencies {
    implementation(project(":backend:platform-core"))
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
