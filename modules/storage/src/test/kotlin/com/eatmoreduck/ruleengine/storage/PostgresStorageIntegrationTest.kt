package com.eatmoreduck.ruleengine.storage

import com.eatmoreduck.ruleengine.domain.FeatureCondition
import com.eatmoreduck.ruleengine.domain.FeatureOperator
import com.eatmoreduck.ruleengine.domain.GrayscalePolicy
import com.eatmoreduck.ruleengine.domain.GrayscaleRelease
import com.eatmoreduck.ruleengine.domain.GrayscaleTarget
import com.eatmoreduck.ruleengine.domain.GrayscaleTargetType
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.domain.RuleVersion
import com.eatmoreduck.ruleengine.domain.VersionStatus
import com.eatmoreduck.ruleengine.storage.feature.FeatureAlias
import com.eatmoreduck.ruleengine.storage.feature.FeatureDefinition
import com.eatmoreduck.ruleengine.storage.repository.ExposedFeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedGrayscaleReleaseRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedRuleRepository
import com.eatmoreduck.ruleengine.storage.repository.ExposedRuleVersionRepository
import com.eatmoreduck.ruleengine.storage.table.FeatureAliasesTable
import com.eatmoreduck.ruleengine.storage.table.FeatureDefinitionsTable
import com.eatmoreduck.ruleengine.storage.table.GrayscaleConfigsTable
import com.eatmoreduck.ruleengine.storage.table.RuleVersionsTable
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

/**
 * 持久化层集成测试：真实 PostgreSQL（Testcontainers）+ Flyway 全量迁移（V1..V26）+ 仓储端到端读写。
 *
 * 这是表对象 ↔ 旧 schema 映射的事实核对：表定义缺列/类型错位会在 CRUD 与
 * [schemaColumnsMatchFlywayBaseline] 的列集合比对中暴露。
 *
 * 注：`disabledWithoutDocker = true` —— 无 Docker 的环境自动跳过整个类（等价 @Disabled 且注明原因：
 * 依赖 Testcontainers 拉起 PostgreSQL 容器），不阻塞构建。
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("PostgreSQL 集成：Flyway 基线 + 仓储 CRUD + JSON 往返")
class PostgresStorageIntegrationTest {
    private lateinit var dataSource: HikariDataSource

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("ruleengine")
                .withUsername("test")
                .withPassword("test")
    }

    private val ruleRepository = ExposedRuleRepository()
    private val versionRepository = ExposedRuleVersionRepository()
    private val grayscaleRepository = ExposedGrayscaleReleaseRepository()
    private val featureRepository = ExposedFeatureCatalogRepository()

    @BeforeAll
    fun startStack() {
        dataSource = hikariFor(postgres)
        Database.connect(dataSource)
        // 与 StorageConfiguration 相同的迁移路径：classpath:db/migration（旧后端 V1..V25 基线 + V26 回填）
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    @AfterAll
    fun stopStack() {
        if (this::dataSource.isInitialized) dataSource.close()
    }

    private fun hikariFor(container: JdbcDatabaseContainer<*>): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = container.jdbcUrl
                username = container.username
                password = container.password
                maximumPoolSize = 4
                poolName = "storage-integration"
            },
        )

    /** 读取 information_schema 中实际列集合（public schema） */
    private fun actualColumns(
        dataSource: DataSource,
        tableName: String,
    ): Set<String> =
        dataSource.connection.use { connection ->
            connection.metaData.getColumns(null, "public", tableName, null).use { rs ->
                buildSet {
                    while (rs.next()) add(rs.getString("COLUMN_NAME"))
                }
            }
        }

    private fun assertColumnsMatch(
        tableName: String,
        exposed: org.jetbrains.exposed.v1.core.Table,
    ) {
        val expected = exposed.columns.map { it.name }.toSet()
        val actual = actualColumns(dataSource, tableName)
        val message =
            "表 $tableName 的列集合与 Flyway 基线不一致；" +
                "Exposed 缺失列=${actual - expected}，多余列=${expected - actual}"
        assertEquals(expected, actual, message)
    }

    @Test
    @DisplayName("Exposed 表对象列集合与 Flyway 基线的实际 schema 完全一致")
    fun schemaColumnsMatchFlywayBaseline() {
        assertColumnsMatch("rules", RulesTable)
        assertColumnsMatch("rule_versions", RuleVersionsTable)
        assertColumnsMatch("grayscale_configs", GrayscaleConfigsTable)
        assertColumnsMatch("feature_definition", FeatureDefinitionsTable)
        assertColumnsMatch("feature_alias", FeatureAliasesTable)
    }

    @Test
    @DisplayName("Flyway 应用迁移数为 25（V1..V26，无 V8）")
    fun flywayAppliedAllMigrations() {
        val applied =
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .info()
                .applied()
        assertEquals(25, applied.size)
    }

    @Test
    @DisplayName("端到端：规则创建 → 版本草稿发布归档 → 灰度启动 → 分流查询命中")
    fun endToEndRuleLifecycle() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        transaction {
            val rule =
                ruleRepository.save(
                    Rule(
                        ruleKey = "pg_e2e_rule",
                        ruleName = "集成链路规则",
                        status = RuleStatus.ENABLED,
                        createdBy = "pg-tester",
                        createdAt = now,
                    ),
                )
            ruleRepository.save(rule.activateVersion(1, "pg-tester", now))
            versionRepository.save(
                RuleVersion(
                    ruleKey = "pg_e2e_rule",
                    version = 1,
                    definitionJson = """{"condition":{"field":"order_amount","operator":"GT","threshold":1000}}""",
                    // 初始版本直接生效（应用层语义：首个版本发布即 ACTIVE）
                    status = VersionStatus.ACTIVE,
                    changedBy = "pg-tester",
                    changedAt = now,
                ),
            )
            versionRepository.save(
                RuleVersion(
                    ruleKey = "pg_e2e_rule",
                    version = 2,
                    definitionJson = """{"condition":{"field":"order_amount","operator":"GT","threshold":2000}}""",
                    changedBy = "pg-tester",
                    changedAt = now,
                ),
            )
        }
        transaction {
            // 发布 v2：旧 ACTIVE(v1) 归档 + v2 发布（应用层两步）
            val oldActive = versionRepository.findByStatus("pg_e2e_rule", VersionStatus.ACTIVE).single()
            versionRepository.save(oldActive.archive())
            val draft = versionRepository.findByRuleKeyAndVersion("pg_e2e_rule", 2)!!
            versionRepository.save(draft.publish())
        }
        transaction {
            val target = GrayscaleTarget(GrayscaleTargetType.RULE, "pg_e2e_rule")
            val release =
                grayscaleRepository.save(
                    GrayscaleRelease(
                        target = target,
                        currentVersion = 1,
                        grayscaleVersion = 2,
                        policy =
                            GrayscalePolicy.Feature(
                                listOf(
                                    FeatureCondition("region", FeatureOperator.EQ, "US"),
                                    FeatureCondition("user_level", FeatureOperator.IN, "VIP,SVIP"),
                                ),
                            ),
                        createdBy = "pg-tester",
                        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                    ),
                )
            grayscaleRepository.save(release.start(Instant.now().truncatedTo(ChronoUnit.MICROS)))
            val running = grayscaleRepository.findRunningByTarget(target)!!
            assertTrue(running.isCanaryRequest(mapOf("region" to "US", "user_level" to "VIP")))
            assertTrue(!running.isCanaryRequest(mapOf("region" to "CN", "user_level" to "VIP")))
            val rule = ruleRepository.findByRuleKey("pg_e2e_rule")!!
            assertEquals(1, rule.currentVersion)
            assertEquals(1, rule.activeVersion)
            assertEquals(RuleStatus.ENABLED, rule.status)
        }
    }

    @Test
    @DisplayName("灰度三种策略在真实 PG 的 JSON/平铺列往返")
    fun grayscalePoliciesRoundTripOnPostgres() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val policies =
            mapOf(
                "pg_gray_pct" to GrayscalePolicy.Percentage(42),
                "pg_gray_wl" to GrayscalePolicy.Whitelist(setOf("u-1", "u-2", "u-3")),
                "pg_gray_ft" to
                    GrayscalePolicy.Feature(
                        listOf(
                            FeatureCondition("risk_score", FeatureOperator.GE, "0.8"),
                            FeatureCondition("device", FeatureOperator.NOT_CONTAINS, "root"),
                        ),
                    ),
            )
        transaction {
            policies.forEach { (key, policy) ->
                grayscaleRepository.save(
                    GrayscaleRelease(
                        target = GrayscaleTarget(GrayscaleTargetType.RULE, key),
                        currentVersion = 1,
                        grayscaleVersion = 2,
                        policy = policy,
                        createdBy = "pg-tester",
                        createdAt = now,
                    ),
                )
            }
        }
        transaction {
            policies.forEach { (key, expected) ->
                val loaded =
                    grayscaleRepository.findByTarget(GrayscaleTarget(GrayscaleTargetType.RULE, key)).single()
                assertEquals(expected, loaded.policy, "灰度策略在 PostgreSQL 往返失真: $key")
            }
        }
    }

    @Test
    @DisplayName("特征目录：V24 种子数据可读、别名解析链路与新增定义落库")
    fun featureCatalogWithSeedData() {
        transaction {
            // V24 迁移自带的种子别名：amount -> order_amount（别名解析事实核对）
            val viaAlias = featureRepository.resolveCode("amount")!!
            assertEquals("order_amount", viaAlias.code)
            assertEquals("订单金额", viaAlias.name)
            val direct = featureRepository.resolveCode("risk_score")!!
            assertEquals("DERIVED", direct.sourceType)
            assertEquals("SENSITIVE", direct.sensitivity)
            assertTrue(featureRepository.findAliasesByCanonicalCode("order_amount").map { it.aliasCode }.contains("amount"))
        }
        transaction {
            val saved =
                featureRepository.saveDefinition(
                    FeatureDefinition(
                        code = "pg_new_feature",
                        name = "集成新增特征",
                        dataType = "BOOLEAN",
                        sourceType = "DERIVED",
                        description = "集成测试写入",
                        scope = "RISK_MODEL",
                        owner = "pg-tester",
                        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                    ),
                )
            assertNotNull(saved.id)
            featureRepository.saveAlias(
                FeatureAlias(
                    aliasCode = "pg_new_feature_alias",
                    canonicalCode = "pg_new_feature",
                    createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
                ),
            )
            assertEquals("pg_new_feature", featureRepository.resolveCode("PG_NEW_FEATURE_ALIAS")!!.code)
        }
    }
}
