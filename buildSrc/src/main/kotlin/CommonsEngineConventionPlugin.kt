import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * 公地引擎 Kotlin + Spring Boot 通用约定。
 *
 * Spring Boot 4.x 不再使用 io.spring.dependency-management 插件，
 * 改用 Gradle 原生 platform() BOM 机制管理依赖版本。
 *
 * 所有 backend/ 子模块 apply 此插件，自动获得：
 * - Kotlin JVM (JDK 21)
 * - Spring Boot BOM（通过 platform 依赖，无需显式版本号）
 * - detekt 代码检查
 */
class CommonsEngineConventionPlugin : Plugin<Project> {
    companion object {
        const val SPRING_BOOT_VERSION = "4.1.0"
    }

    override fun apply(project: Project) {
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.kotlin.plugin.spring")
        project.plugins.apply("io.gitlab.arturbosch.detekt")

        project.extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        // Spring Boot 4.x: 用 Gradle platform() 代替 dependency-management 插件
        project.dependencies {
            add("implementation", platform("org.springframework.boot:spring-boot-dependencies:$SPRING_BOOT_VERSION"))
            add("testImplementation", platform("org.springframework.boot:spring-boot-dependencies:$SPRING_BOOT_VERSION"))
            add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
        }

        project.extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(project.rootProject.files("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
        }
    }
}
