package com.eatmoreduck.ruleengine.storage

import com.eatmoreduck.ruleengine.domain.FeatureCondition
import com.eatmoreduck.ruleengine.domain.FeatureOperator
import com.eatmoreduck.ruleengine.domain.GrayscalePolicy
import com.eatmoreduck.ruleengine.domain.GrayscaleRelease
import com.eatmoreduck.ruleengine.domain.GrayscaleStatus
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
import com.eatmoreduck.ruleengine.storage.repository.FeatureCatalogRepository
import com.eatmoreduck.ruleengine.storage.repository.FeatureDefinitionQuery
import com.eatmoreduck.ruleengine.storage.repository.GrayscaleReleaseRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import com.eatmoreduck.ruleengine.storage.table.FeatureAliasesTable
import com.eatmoreduck.ruleengine.storage.table.FeatureDefinitionsTable
import com.eatmoreduck.ruleengine.storage.table.GrayscaleConfigsTable
import com.eatmoreduck.ruleengine.storage.table.RuleVersionsTable
import com.eatmoreduck.ruleengine.storage.table.RulesTable
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 行 ↔ 领域模型映射单元测试（H2 内存库，SchemaUtils 生成 DDL）。
 *
 * 覆盖映射层易错点：三态规则状态 ↔ enabled/deleted 双列、版本状态枚举、回滚标志 NULL 兜底、
 * 灰度 sealed 策略 ↔ 平铺列往返、时间戳微秒截断、特征别名大小写不敏感解析。
 * 每个用例自建数据，互不依赖执行顺序；真实 PostgreSQL schema 的核对由
 * PostgresStorageIntegrationTest（Testcontainers）承担。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class H2StorageMappingTest {
    private val ruleRepository: RuleRepository = ExposedRuleRepository()
    private val versionRepository: RuleVersionRepository = ExposedRuleVersionRepository()
    private val grayscaleRepository: GrayscaleReleaseRepository = ExposedGrayscaleReleaseRepository()
    private val featureRepository: FeatureCatalogRepository = ExposedFeatureCatalogRepository()

    @BeforeAll
    fun connectAndCreateSchema() {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:storage-mapping;DB_CLOSE_DELAY=-1")
            }
        Database.connect(dataSource)
        transaction {
            SchemaUtils.create(RulesTable, RuleVersionsTable, GrayscaleConfigsTable, FeatureDefinitionsTable, FeatureAliasesTable)
        }
    }

    @AfterAll
    fun dropSchema() {
        transaction {
            SchemaUtils.drop(RulesTable, RuleVersionsTable, GrayscaleConfigsTable, FeatureDefinitionsTable, FeatureAliasesTable)
        }
    }

    /** PG TIMESTAMP（无时区）只保留微秒：构造微秒对齐的时间戳，避免写入纳秒造成假失败 */
    private fun instant(iso: String): Instant = Instant.parse(iso).truncatedTo(ChronoUnit.MICROS)

    private fun newRule(
        ruleKey: String,
        status: RuleStatus = RuleStatus.ENABLED,
    ): Rule =
        Rule(
            ruleKey = ruleKey,
            ruleName = "规则-$ruleKey",
            ruleDescription = "描述-$ruleKey",
            status = status,
            currentVersion = 3,
            activeVersion = 2,
            environmentId = 1L,
            teamId = 7L,
            createdBy = "tester",
            createdAt = instant("2026-01-02T03:04:05.123456Z"),
            updatedBy = "tester2",
            updatedAt = instant("2026-01-02T04:04:05.123456Z"),
        )

    private fun newVersion(
        ruleKey: String,
        version: Int,
        status: VersionStatus,
        rollbackFrom: Int? = null,
    ): RuleVersion =
        RuleVersion(
            ruleKey = ruleKey,
            version = version,
            definitionJson = """{"condition":{"field":"order_amount","operator":"GT","threshold":$version}}""",
            status = status,
            changeReason = "变更-$version",
            changedBy = "tester",
            changedAt = instant("2026-01-0${version}T00:00:00Z"),
            isRollback = rollbackFrom != null,
            rollbackFromVersion = rollbackFrom,
        )

    // ---------- Rule ----------

    @Nested
    @DisplayName("规则行 ↔ 领域模型")
    inner class RuleMapping {
        @Test
        fun `insert 后 id 回填且全部列往返一致`() {
            val saved = transaction { ruleRepository.save(newRule("h2_rule_insert")) }
            assertNotNull(saved.id)
            val loaded = transaction { ruleRepository.findByRuleKey("h2_rule_insert") }!!
            assertEquals(saved, loaded)
            assertEquals(RuleStatus.ENABLED, loaded.status)
            assertEquals(3, loaded.currentVersion)
            assertEquals(2, loaded.activeVersion)
            assertEquals("tester2", loaded.updatedBy)
        }

        @Test
        fun `DISABLED 状态经 enabled=false 列往返保持`() {
            transaction { ruleRepository.save(newRule("h2_rule_disabled", RuleStatus.DISABLED)) }
            val loaded = transaction { ruleRepository.findByRuleKey("h2_rule_disabled") }!!
            assertEquals(RuleStatus.DISABLED, loaded.status)
        }

        @Test
        fun `DELETED 状态以 deleted 列承载且读取路径不可见`() {
            transaction { ruleRepository.save(newRule("h2_rule_deleted", RuleStatus.DELETED)) }
            // findByRuleKey 排除软删行（同名重建语义），DELETED 行以 existsActiveMain/existsByRuleKey 语义判定
            val loaded = transaction { ruleRepository.findByRuleKey("h2_rule_deleted") }
            assertNull(loaded)
            assertFalse(transaction { ruleRepository.existsByRuleKey("h2_rule_deleted") })
            val enabledRules = transaction { ruleRepository.findEnabled() }
            assertFalse(enabledRules.any { it.ruleKey == "h2_rule_deleted" })
        }

        @Test
        fun `update 按 id 全列更新不新增行`() {
            val saved = transaction { ruleRepository.save(newRule("h2_rule_update")) }
            val renamed =
                saved.rename(ruleName = "更新后名称", operator = "editor", at = instant("2026-03-02T00:00:00Z"))
            transaction { ruleRepository.save(renamed) }
            val loaded = transaction { ruleRepository.findByRuleKey("h2_rule_update") }!!
            assertEquals("更新后名称", loaded.ruleName)
            assertEquals("editor", loaded.updatedBy)
            assertEquals(saved.id, loaded.id)
        }

        @Test
        fun `existsByRuleKey 与 findEnabled 过滤`() {
            transaction { ruleRepository.save(newRule("h2_rule_exists")) }
            assertTrue(transaction { ruleRepository.existsByRuleKey("h2_rule_exists") })
            assertFalse(transaction { ruleRepository.existsByRuleKey("h2_rule_missing") })
            assertTrue(transaction { ruleRepository.findEnabled().any { it.ruleKey == "h2_rule_exists" } })
            val scoped = transaction { ruleRepository.findEnabled(environmentId = 1L) }
            assertTrue(scoped.any { it.ruleKey == "h2_rule_exists" })
            assertTrue(transaction { ruleRepository.findEnabled(environmentId = 999L).none { it.ruleKey == "h2_rule_exists" } })
        }

        @Test
        fun `search 关键字命中 rule_key 与 rule_name 且默认排除软删除`() {
            transaction {
                ruleRepository.save(newRule("h2_search_antifraud").copy(ruleName = "欺诈拦截专项"))
                ruleRepository.save(newRule("h2_search_gone", RuleStatus.DELETED))
            }
            // 仅 rule_key 含关键字（ruleName="欺诈拦截专项" 不含）
            val byKey = transaction { ruleRepository.search(RuleSearchQuery(keyword = "ANTIFRAUD")) }
            assertTrue(byKey.any { it.ruleKey == "h2_search_antifraud" })
            // 仅 rule_name 含关键字（ruleKey 不含"拦截"）
            val byName = transaction { ruleRepository.search(RuleSearchQuery(keyword = "拦截")) }
            assertTrue(byName.any { it.ruleKey == "h2_search_antifraud" })
            val defaultView = transaction { ruleRepository.search(RuleSearchQuery(keyword = "h2_search")) }
            assertTrue(defaultView.any { it.ruleKey == "h2_search_antifraud" })
            assertFalse(defaultView.any { it.ruleKey == "h2_search_gone" })
            val withDeleted = transaction { ruleRepository.search(RuleSearchQuery(keyword = "h2_search", includeDeleted = true)) }
            assertTrue(withDeleted.any { it.ruleKey == "h2_search_gone" })
        }

        @Test
        fun `search 分页 limit offset`() {
            transaction {
                (1..5).forEach { index ->
                    ruleRepository.save(newRule("h2_paged_%02d".format(index)))
                }
            }
            val page = transaction { ruleRepository.search(RuleSearchQuery(keyword = "h2_paged", limit = 2, offset = 1)) }
            assertEquals(2, page.size)
        }
    }

    // ---------- RuleVersion ----------

    @Nested
    @DisplayName("版本行 ↔ 领域模型与状态机查询")
    inner class RuleVersionMapping {
        @Test
        fun `定义载荷原样存取且回滚标志往返`() {
            transaction {
                ruleRepository.save(newRule("h2_ver_a"))
                val version =
                    newVersion("h2_ver_a", version = 1, status = VersionStatus.DRAFT, rollbackFrom = 9)
                        .copy(changeReason = "回滚到 9 号版本定义")
                versionRepository.save(version)
                val loaded = versionRepository.findByRuleKeyAndVersion("h2_ver_a", 1)!!
                assertTrue(loaded.isRollback)
                assertEquals(9, loaded.rollbackFromVersion)
                assertTrue(loaded.definitionJson.contains("order_amount"))
            }
        }

        @Test
        fun `findCurrentVersion 取版本号最大者，findActiveVersion 仅看 ACTIVE 状态`() {
            transaction {
                ruleRepository.save(newRule("h2_ver_b"))
                versionRepository.save(newVersion("h2_ver_b", 2, VersionStatus.ACTIVE))
                versionRepository.save(newVersion("h2_ver_b", 3, VersionStatus.DRAFT))
            }
            transaction {
                assertEquals(3, versionRepository.findCurrentVersion("h2_ver_b")!!.version)
                val active = versionRepository.findActiveVersion("h2_ver_b")!!
                assertEquals(2, active.version)
                assertEquals(VersionStatus.ACTIVE, active.status)
            }
        }

        @Test
        fun `发布-归档状态机：旧 ACTIVE 归档、新 DRAFT 发布后成为唯一 ACTIVE`() {
            transaction {
                ruleRepository.save(newRule("h2_ver_c"))
                versionRepository.save(newVersion("h2_ver_c", 2, VersionStatus.ACTIVE))
                versionRepository.save(newVersion("h2_ver_c", 3, VersionStatus.DRAFT))
                // 应用层发布流程：旧 ACTIVE 归档 + 新版本发布（两次独立 save 落库）
                val oldActive = versionRepository.findByStatus("h2_ver_c", VersionStatus.ACTIVE).single()
                versionRepository.save(oldActive.archive())
                val draft = versionRepository.findByRuleKeyAndVersion("h2_ver_c", 3)!!
                versionRepository.save(draft.publish())
            }
            transaction {
                assertEquals(3, versionRepository.findActiveVersion("h2_ver_c")!!.version)
                val archived = versionRepository.findByStatus("h2_ver_c", VersionStatus.ARCHIVED)
                assertTrue(archived.any { it.version == 2 })
                assertEquals(1, versionRepository.findByStatus("h2_ver_c", VersionStatus.ACTIVE).size)
            }
        }

        @Test
        fun `非回滚版本的 rollback_from_version 列保持 NULL`() {
            transaction {
                ruleRepository.save(newRule("h2_ver_d"))
                versionRepository.save(newVersion("h2_ver_d", 2, VersionStatus.CANARY))
            }
            val loaded = transaction { versionRepository.findByRuleKeyAndVersion("h2_ver_d", 2) }!!
            assertFalse(loaded.isRollback)
            assertNull(loaded.rollbackFromVersion)
            assertEquals(VersionStatus.CANARY, loaded.status)
        }

        @Test
        fun `无版本规则查询返回空`() {
            transaction {
                assertNull(versionRepository.findCurrentVersion("h2_ver_missing"))
                assertNull(versionRepository.findActiveVersion("h2_ver_missing"))
                assertTrue(versionRepository.findByRuleKey("h2_ver_missing").isEmpty())
                assertFalse(versionRepository.existsByRuleKeyAndVersion("h2_ver_missing", 1))
            }
        }

        @Test
        fun `findByRuleKey 按版本号降序`() {
            transaction {
                ruleRepository.save(newRule("h2_ver_e"))
                versionRepository.save(newVersion("h2_ver_e", 1, VersionStatus.ARCHIVED))
                versionRepository.save(newVersion("h2_ver_e", 2, VersionStatus.ACTIVE))
                versionRepository.save(newVersion("h2_ver_e", 3, VersionStatus.DRAFT))
            }
            val versions = transaction { versionRepository.findByRuleKey("h2_ver_e") }
            assertEquals(listOf(3, 2, 1), versions.map { it.version })
        }
    }

    // ---------- GrayscaleRelease ----------

    @Nested
    @DisplayName("灰度行 ↔ 领域模型（sealed 策略 ↔ 平铺列）")
    inner class GrayscaleMapping {
        private fun saveAndReloadByTargetKey(
            release: GrayscaleRelease,
            targetKey: String,
        ): GrayscaleRelease {
            transaction { grayscaleRepository.save(release) }
            return transaction {
                grayscaleRepository.findByTarget(GrayscaleTarget(release.target.type, targetKey)).single()
            }
        }

        private fun draftRelease(
            targetKey: String,
            type: GrayscaleTargetType = GrayscaleTargetType.RULE,
            policy: GrayscalePolicy,
        ): GrayscaleRelease =
            GrayscaleRelease(
                target = GrayscaleTarget(type, targetKey),
                currentVersion = 1,
                grayscaleVersion = 2,
                policy = policy,
                createdBy = "tester",
                createdAt = instant("2026-05-01T00:00:00Z"),
            )

        @Test
        fun `Percentage 策略往返`() {
            val release = draftRelease("h2_gray_pct", policy = GrayscalePolicy.Percentage(25))
            val loaded = saveAndReloadByTargetKey(release, "h2_gray_pct")
            assertEquals(GrayscalePolicy.Percentage(25), loaded.policy)
            assertEquals(GrayscaleStatus.DRAFT, loaded.status)
        }

        @Test
        fun `Whitelist 策略往返`() {
            val release = draftRelease("h2_gray_wl", policy = GrayscalePolicy.Whitelist(setOf("vip-1", "vip-2")))
            val loaded = saveAndReloadByTargetKey(release, "h2_gray_wl")
            assertEquals(GrayscalePolicy.Whitelist(setOf("vip-1", "vip-2")), loaded.policy)
        }

        @Test
        fun `Feature 策略 JSON 往返（多条件全部还原）`() {
            val policy =
                GrayscalePolicy.Feature(
                    listOf(
                        FeatureCondition("region", FeatureOperator.IN, "US,CA"),
                        FeatureCondition("order_amount", FeatureOperator.GE, "500"),
                        FeatureCondition("device", FeatureOperator.CONTAINS, "iPhone"),
                        FeatureCondition("risk_score", FeatureOperator.LT, "0.5"),
                    ),
                )
            val release = draftRelease("h2_gray_ft", policy = policy)
            val loaded = saveAndReloadByTargetKey(release, "h2_gray_ft")
            assertEquals(policy, loaded.policy)
        }

        @Test
        fun `dualRunEnabled 与 description 列往返`() {
            val release =
                draftRelease("h2_gray_meta", policy = GrayscalePolicy.Percentage(30))
                    .copy(dualRunEnabled = true, description = "双跑对比灰度")
            val loaded = saveAndReloadByTargetKey(release, "h2_gray_meta")
            assertTrue(loaded.dualRunEnabled)
            assertEquals("双跑对比灰度", loaded.description)
        }

        @Test
        fun `DRAFT 无 startedAt，start 后可被 RUNNING 分流查询命中`() {
            transaction {
                val target = GrayscaleTarget(GrayscaleTargetType.RULE, "h2_gray_run")
                val release = grayscaleRepository.save(draftRelease("h2_gray_run", policy = GrayscalePolicy.Percentage(50)))
                assertNull(grayscaleRepository.findRunningByTarget(target))
                grayscaleRepository.save(release.start(instant("2026-05-04T08:00:00Z")))
                val running = grayscaleRepository.findRunningByTarget(target)!!
                assertEquals(GrayscaleStatus.RUNNING, running.status)
                assertEquals(instant("2026-05-04T08:00:00Z"), running.startedAt)
                assertNull(running.completedAt)
            }
        }

        @Test
        fun `DECISION_FLOW 目标类型往返且 rule_key 兼容列同值`() {
            val release =
                draftRelease("h2_gray_flow", type = GrayscaleTargetType.DECISION_FLOW, policy = GrayscalePolicy.Percentage(10))
                    .copy(currentVersion = 5, grayscaleVersion = 6)
            saveAndReloadByTargetKey(release, "h2_gray_flow")
            transaction {
                val loaded =
                    grayscaleRepository.findByTarget(GrayscaleTarget(GrayscaleTargetType.DECISION_FLOW, "h2_gray_flow")).single()
                assertEquals(GrayscaleTargetType.DECISION_FLOW, loaded.target.type)
                // 兼容列与 target_key 同值（旧索引 idx_grayscale_configs_rule_key 继续有效）
                val row =
                    GrayscaleConfigsTable
                        .selectAll()
                        .single { it[GrayscaleConfigsTable.targetKey] == "h2_gray_flow" }
                assertEquals("h2_gray_flow", row[GrayscaleConfigsTable.ruleKey])
            }
        }

        @Test
        fun `状态迁移后 save 原地更新同一行`() {
            transaction {
                val target = GrayscaleTarget(GrayscaleTargetType.RULE, "h2_gray_pause")
                val saved = grayscaleRepository.save(draftRelease("h2_gray_pause", policy = GrayscalePolicy.Percentage(5)))
                val id = saved.id
                grayscaleRepository.save(saved.start(instant("2026-05-06T01:00:00Z")).pause())
                val rows = grayscaleRepository.findByTarget(target)
                assertEquals(1, rows.size)
                assertEquals(GrayscaleStatus.PAUSED, rows.single().status)
                assertEquals(id, rows.single().id)
                assertEquals(instant("2026-05-06T01:00:00Z"), rows.single().startedAt)
            }
        }

        @Test
        fun `findByStatus 与 findAll 按创建时间降序`() {
            transaction {
                val running =
                    draftRelease("h2_gray_list_1", policy = GrayscalePolicy.Percentage(60))
                        .copy(status = GrayscaleStatus.DRAFT)
                grayscaleRepository.save(running.start(instant("2026-05-07T01:00:00Z")))
                grayscaleRepository.save(draftRelease("h2_gray_list_2", policy = GrayscalePolicy.Percentage(70)))
            }
            val runningAll = transaction { grayscaleRepository.findByStatus(GrayscaleStatus.RUNNING) }
            assertTrue(runningAll.any { it.target.key == "h2_gray_list_1" })
            val all = transaction { grayscaleRepository.findAll() }
            assertTrue(all.size >= 2)
        }
    }

    // ---------- FeatureCatalog ----------

    @Nested
    @DisplayName("特征目录与别名")
    inner class FeatureCatalogMapping {
        private fun newDefinition(
            code: String,
            status: String = "ACTIVE",
        ): FeatureDefinition =
            FeatureDefinition(
                code = code,
                name = "测试特征-$code",
                dataType = "NUMBER",
                sourceType = "INPUT",
                exampleValue = "42",
                sensitivity = "SENSITIVE",
                status = status,
                owner = "risk-team",
                createdAt = instant("2026-06-01T00:00:00Z"),
                updatedAt = instant("2026-06-02T00:00:00Z"),
            )

        @Test
        fun `定义 insert 后 id 回填且大小写不敏感查询`() {
            val saved = transaction { featureRepository.saveDefinition(newDefinition("h2_fc_def_one")) }
            assertNotNull(saved.id)
            val loaded = transaction { featureRepository.findDefinitionByCode("H2_FC_DEF_ONE") }!!
            assertEquals("h2_fc_def_one", loaded.code)
            assertTrue(transaction { featureRepository.existsDefinitionWithCode("h2_FC_DEF_ONE") })
        }

        @Test
        fun `别名解析：直接编码未命中时经别名跳转到规范定义`() {
            transaction {
                featureRepository.saveDefinition(newDefinition("h2_fc_def_two"))
                featureRepository.saveAlias(
                    FeatureAlias(
                        aliasCode = "h2_fc_alias_two",
                        canonicalCode = "h2_fc_def_two",
                        createdAt = instant("2026-06-03T00:00:00Z"),
                    ),
                )
            }
            transaction {
                assertEquals("h2_fc_def_two", featureRepository.resolveCode("h2_fc_def_two")!!.code)
                // 别名编码大小写不敏感
                assertEquals("h2_fc_def_two", featureRepository.resolveCode("H2_FC_ALIAS_TWO")!!.code)
                assertNull(featureRepository.resolveCode("h2_fc_unknown"))
            }
        }

        @Test
        fun `非 ACTIVE 状态的定义与别名不参与解析`() {
            transaction {
                featureRepository.saveDefinition(newDefinition("h2_fc_def_inactive", status = "DISABLED"))
                featureRepository.saveDefinition(newDefinition("h2_fc_def_aliasable"))
                featureRepository.saveAlias(
                    FeatureAlias(
                        aliasCode = "h2_fc_alias_inactive",
                        canonicalCode = "h2_fc_def_inactive",
                        status = "ARCHIVED",
                        createdAt = instant("2026-06-04T00:00:00Z"),
                    ),
                )
                featureRepository.saveAlias(
                    FeatureAlias(
                        aliasCode = "h2_fc_alias_ok",
                        canonicalCode = "h2_fc_def_aliasable",
                        createdAt = instant("2026-06-04T00:00:00Z"),
                    ),
                )
            }
            transaction {
                assertNull(featureRepository.resolveCode("h2_fc_def_inactive"))
                assertNull(featureRepository.resolveCode("h2_fc_alias_inactive"))
                assertEquals("h2_fc_def_aliasable", featureRepository.resolveCode("h2_fc_alias_ok")!!.code)
            }
        }

        @Test
        fun `批量按编码加载保持入参顺序且跳过缺失`() {
            transaction {
                featureRepository.saveDefinition(newDefinition("h2_fc_def_first"))
                featureRepository.saveDefinition(newDefinition("h2_fc_def_second"))
            }
            val loaded =
                transaction {
                    featureRepository.findDefinitionsByCodes(listOf("h2_fc_def_second", "h2_fc_missing", "H2_FC_DEF_FIRST"))
                }
            assertEquals(listOf("h2_fc_def_second", "h2_fc_def_first"), loaded.map { it.code })
        }

        @Test
        fun `组合条件检索与别名清理`() {
            transaction {
                featureRepository.saveDefinition(newDefinition("h2_fc_def_search"))
                featureRepository.saveAlias(
                    FeatureAlias(
                        aliasCode = "h2_fc_alias_search",
                        canonicalCode = "h2_fc_def_search",
                        createdAt = instant("2026-06-05T00:00:00Z"),
                    ),
                )
                val hit =
                    featureRepository.searchDefinitions(
                        FeatureDefinitionQuery(
                            keyword = "h2_fc_def_search",
                            dataType = "NUMBER",
                            sensitivity = "SENSITIVE",
                        ),
                    )
                assertTrue(hit.any { it.code == "h2_fc_def_search" })
                val miss = featureRepository.searchDefinitions(FeatureDefinitionQuery(keyword = "h2_fc_def_search", sensitivity = "NORMAL"))
                assertTrue(miss.isEmpty())

                assertEquals(1, featureRepository.deleteAliasesByCanonicalCode("H2_FC_DEF_SEARCH"))
                assertNull(featureRepository.findAliasByCode("h2_fc_alias_search"))
                // 别名删除后别名链路不再解析
                assertNull(featureRepository.resolveCode("h2_fc_alias_search"))
            }
        }
    }
}
