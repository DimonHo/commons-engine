package com.commonsengine.platform.postgres

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 真实 PostgreSQL 集成测试（嵌入式 Postgres，无需 docker / root）。
 *
 * 用途：弥补 H2 测试 profile 的盲区。H2 profile 关闭 Flyway、用 ddl-auto=create-drop
 * 由 Hibernate 自动建表，因此无法验证「默认 profile（Flyway 迁移 + ddl-auto=validate）
 * 在真实 PostgreSQL 上能否正确启动」——而这正是 README「可运行 demo」的实际运行路径。
 *
 * 本测试以接近默认 profile 的配置（Flyway 启用、ddl-auto=validate、PostgreSQL 方言）
 * 启动整个应用上下文，断言：
 *  1. 上下文正常加载（即 Flyway 真正执行了迁移、Hibernate validate 通过）
 *  2. 关键表存在：members / worker_locations / ledger_events
 *
 * 关联：demo.yml CI 失败的根因定位；#35 阶段1 收尾；#44 PostGIS。
 */
@SpringBootTest
@ActiveProfiles("postgres-it") // 见 application-postgres-it.yml：Flyway 开 + ddl-auto=validate
class PostgresFlywayIntegrationTest {

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `context loads against real PostgreSQL with Flyway migrations`() {
        // 上下文成功加载本身就是核心断言：Flyway 迁移已执行 + Hibernate validate 通过
    }

    @Test
    fun `core tables created by Flyway exist`() {
        val nativeQuery = entityManager.createNativeQuery(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN ('members','worker_locations','ledger_events','worker_profiles')
            """.trimIndent(),
        )
        @Suppress("UNCHECKED_CAST")
        val tables = nativeQuery.resultList as List<String>
        assertTrue(
            tables.contains("ledger_events"),
            "ledger_events 表应由 Flyway V3 创建；缺失意味着 Flyway 未运行。实际表: $tables",
        )
        assertTrue(tables.contains("members"), "members 表应由 V1 创建。实际: $tables")
        assertTrue(tables.contains("worker_locations"), "worker_locations 表应由 V2 创建。实际: $tables")
    }

    @Test
    fun `flyway history table recorded all three migrations`() {
        val nativeQuery = entityManager.createNativeQuery(
            "SELECT count(*) FROM flyway_schema_history WHERE type = 'SQL'",
        )
        val count = (nativeQuery.singleResult as Number).toLong()
        assertTrue(count >= 3, "应至少记录 3 条 SQL 迁移（V1/V2/V3）。实际: $count")
    }

    companion object {
        @JvmStatic
        private lateinit var embeddedPostgres: EmbeddedPostgres

        @BeforeAll
        @JvmStatic
        fun startPostgres() {
            embeddedPostgres = EmbeddedPostgres.builder().start()
        }

        @AfterAll
        @JvmStatic
        fun stopPostgres() {
            if (::embeddedPostgres.isInitialized) {
                embeddedPostgres.close()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProps(registry: DynamicPropertyRegistry) {
            // 用嵌入式真实 PostgreSQL 覆盖数据源；其余配置走 application-postgres-it.yml
            // （确保与 demo 默认运行路径一致：Flyway 启用、ddl-auto=validate）
            registry.add("spring.datasource.url") { embeddedPostgres.getJdbcUrl("postgres", "postgres") }
            registry.add("spring.datasource.username") { "postgres" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}
