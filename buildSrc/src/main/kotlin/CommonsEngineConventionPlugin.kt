import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import io.spring.gradle.dependencymanagement.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

/**
 * 公地引擎 Kotlin + Spring Boot 通用约定。
 *
 * 所有 backend/ 子模块 apply 此插件，自动获得：
 * - Kotlin JVM (JDK 21)
 * - io.spring.dependency-management（含 Spring Boot BOM）
 * - detekt
 */
class CommonsEngineConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("org.jetbrains.kotlin.plugin.spring")
        project.plugins.apply("io.spring.dependency-management")
        project.plugins.apply("io.gitlab.arturbosch.detekt")

        project.extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        project.extensions.configure<DependencyManagementExtension> {
            imports {
                mavenBom(SpringBootPlugin.BOM_COORDINATES)
            }
        }

        project.dependencies {
            add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
        }

        project.extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(project.rootProject.files("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
        }
    }
}
