plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.spring") version "2.1.0" apply false
    id("org.springframework.boot") version "4.1.0" apply false
    id("org.graalvm.buildtools.native") version "0.10.6" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

group = "com.commonsengine"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}
