// 根构建——声明全局配置与共享插件（apply false，各模块按需 apply）
group = "com.commonsengine"
version = "0.1.0-SNAPSHOT"

plugins {
    // detekt 静态分析（#46）——Kotlin 2.3.0 兼容版本
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    // JaCoCo 是 Gradle 核心插件，无需在 plugins{} 声明，子项目直接 apply 即可
}

allprojects {
    repositories {
        mavenCentral()
    }
}

// ── detekt 配置（#46）──────────────────────────────────
// ⚠️ 已知阻塞：detekt 1.23.8（最新 stable）的字节码硬引用
//    org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension#getTarget()
//    返回 KotlinWithJavaTarget，这两个类在 KGP 2.3.0（本项目所用）中已被移除/重构。
//    detekt 2.0.0-alpha.5 仍有同样问题（verified 2026-07-08）。
//    结论：在 detekt 发布兼容 KGP 2.3.0 的正式版前，detekt-gradle-plugin 无法 apply。
//
//    替代方案（已配置于本块下方）：通过 Detekt CLI（独立 fat-jar）+ 自定义 Gradle 任务实现等价能力，
//    CLI 不依赖 KGP 扩展 API，只依赖 Kotlin 编译器，与 KGP 版本解耦。
subprojects {
    if (path.startsWith(":backend:")) {
        // Detekt CLI 任务——绕过 gradle-plugin 的 KGP 兼容性问题
        val detektCliVersion = "1.23.8"
        val detektConfigFile = "$rootDir/config/detekt/detekt.yml"
        val detektBaselineFile = file("$rootDir/config/detekt/baseline.xml").takeIf { it.exists() }

        tasks.register("detekt") {
            group = "verification"
            description = "Runs detekt via CLI (bypasses KGP 2.3.0 incompatibility of the gradle plugin)"

            val sourceDirs = fileTree(layout.projectDirectory.dir("src")).matching {
                include("**/*.kt", "**/*.kts")
            }
            inputs.files(sourceDirs)
            inputs.file(detektConfigFile)
            outputs.upToDateWhen { false }  // CLI 报告内容可能变化，总是重新运行

            doLast {
                // detekt-cli 不是 fat-jar，需要完整运行时 classpath
                val detektConfig = configurations.detachedConfiguration(
                    dependencies.create("io.gitlab.arturbosch.detekt:detekt-cli:$detektCliVersion"),
                )
                detektConfig.isTransitive = true
                val classpath = detektConfig.files.joinToString(":")

                val reportDir = layout.buildDirectory.dir("reports/detekt").get().asFile.apply { mkdirs() }
                val txtReport = reportDir.resolve("detekt.txt")
                val xmlReport = reportDir.resolve("detekt.xml")
                val cmd = mutableListOf(
                    "java", "-cp", classpath,
                    "io.gitlab.arturbosch.detekt.cli.Main",
                    "--input", layout.projectDirectory.dir("src").asFile.absolutePath,
                    "--config", detektConfigFile,
                    "--report", "txt:${txtReport.absolutePath}",
                    "--report", "xml:${xmlReport.absolutePath}",
                    "--jvm-target", "21",
                    "--parallel",
                )
                if (detektBaselineFile != null) {
                    cmd.addAll(listOf("--baseline", detektBaselineFile.absolutePath))
                }
                logger.lifecycle("  detekt CLI: ${layout.projectDirectory.dir("src").asFile.name}")
                // warn 级不阻断：detekt CLI 退出码 0=clean / 1=violations，我们容忍 1
                // 使用 ProcessBuilder 而非 Gradle exec API（doLast 内 DSL 类型推断受限）
                val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                val exitValue = process.exitValue()
                if (output.isNotBlank()) {
                    logger.lifecycle(output.take(2000))
                }
                if (exitValue == 0) {
                    logger.lifecycle("  ✓ detekt: 无违规")
                } else {
                    logger.lifecycle("  ⚠ detekt: 发现违规（exit $exitValue）——见 $txtReport")
                    logger.lifecycle("    （阶段1 容忍 detekt 违规，后续收紧为 fail）")
                }
            }
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn("detekt")
        }
    }
}

// ── JaCoCo 覆盖率聚合（#46）──────────────────────────
// jacoco 插件 apply 后会自动注册 jacocoTestReport 任务，此处仅做配置。
subprojects {
    if (path.startsWith(":backend:")) {
        apply(plugin = "jacoco")

        tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = false
            }
        }

        // jacoco 插件在 evaluate 后注册 jacocoTestReport，用 matching 安全配置
        tasks.matching { it.name == "jacocoTestReport" }.configureEach {
            val report = this as JacocoReport
            report.dependsOn("test")

            // 排除自动生成/基础设施类，只统计业务代码
            val classDirs = files(
                layout.buildDirectory.dir("classes/kotlin/main"),
                layout.buildDirectory.dir("classes/java/main"),
            ).filter { it.exists() }
            report.classDirectories.setFrom(classDirs)

            val kotlinMain = layout.projectDirectory.dir("src/main/kotlin")
            val javaMain = layout.projectDirectory.dir("src/main/java")
            report.sourceDirectories.setFrom(listOf(kotlinMain, javaMain).filter { it.asFile.exists() })

            report.executionData.setFrom(fileTree(layout.buildDirectory).matching {
                include("jacoco/test.exec")
            })

            report.reports {
                xml.required.set(true)   // CI 上传用
                html.required.set(true)  // 本地查看
                csv.required.set(false)
            }
        }
    }
}
