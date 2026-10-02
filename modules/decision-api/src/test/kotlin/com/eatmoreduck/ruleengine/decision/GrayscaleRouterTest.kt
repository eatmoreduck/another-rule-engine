package com.eatmoreduck.ruleengine.decision

import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.eatmoreduck.ruleengine.decision.core.GrayscaleRouter
import com.eatmoreduck.ruleengine.decision.core.RuleSnapshotOutcome
import com.eatmoreduck.ruleengine.decision.repo.DecisionFlowReader
import com.eatmoreduck.ruleengine.decision.repo.GrayscaleMetricsCounter
import com.eatmoreduck.ruleengine.domain.GrayscalePolicy
import com.eatmoreduck.ruleengine.domain.GrayscaleRelease
import com.eatmoreduck.ruleengine.domain.GrayscaleStatus
import com.eatmoreduck.ruleengine.domain.GrayscaleTarget
import com.eatmoreduck.ruleengine.domain.GrayscaleTargetType
import com.eatmoreduck.ruleengine.domain.Rule
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.domain.RuleVersion
import com.eatmoreduck.ruleengine.domain.VersionStatus
import com.eatmoreduck.ruleengine.storage.repository.DecisionFlowMain
import com.eatmoreduck.ruleengine.storage.repository.DecisionFlowVersion
import com.eatmoreduck.ruleengine.storage.repository.GrayscaleReleaseRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import com.eatmoreduck.ruleengine.storage.repository.RuleVersionRepository
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 版本钉住 + 灰度分流路由单测（fake 仓储，无 DB）：
 * 覆盖灰度命中/未命中/配置缺失下的版本钉住、灰度版本缺失回退、缓存命中、停用规则拒绝。
 *
 * Router 内部以 `transaction { }` 包裹仓储调用（Exposed 事务上下文约定），
 * fake 仓储不执行 SQL，仅需一个空连接满足上下文解析。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrayscaleRouterTest {
    private val now = Instant.parse("2026-09-30T00:00:00Z")

    @BeforeAll
    fun connectEmptyDatabase() {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:grayscale-router;DB_CLOSE_DELAY=-1")
            }
        Database.connect(dataSource)
    }

    // ---------- fakes ----------

    private class FakeRuleRepository : RuleRepository {
        val rules = mutableMapOf<String, Rule>()
        var lookups = 0

        override fun purgeDeleted(ruleKey: String) {
            rules.remove(ruleKey)
        }

        override fun save(rule: Rule): Rule {
            rules[rule.ruleKey] = rule
            return rule
        }

        override fun findByRuleKey(ruleKey: String): Rule? {
            lookups++
            return rules[ruleKey]
        }

        override fun existsByRuleKey(ruleKey: String): Boolean = rules.containsKey(ruleKey)

        override fun findEnabled(environmentId: Long?): List<Rule> = rules.values.filter { it.status == RuleStatus.ENABLED }

        override fun search(query: RuleSearchQuery): List<Rule> = rules.values.toList()
    }

    private class FakeRuleVersionRepository : RuleVersionRepository {
        val versions = mutableMapOf<Pair<String, Int>, RuleVersion>()
        var lookups = 0

        override fun save(version: RuleVersion): RuleVersion {
            versions[version.ruleKey to version.version] = version
            return version
        }

        override fun findByRuleKeyAndVersion(
            ruleKey: String,
            version: Int,
        ): RuleVersion? {
            lookups++
            return versions[ruleKey to version]
        }

        override fun existsByRuleKeyAndVersion(
            ruleKey: String,
            version: Int,
        ): Boolean = versions.containsKey(ruleKey to version)

        override fun findByRuleKey(ruleKey: String): List<RuleVersion> =
            versions.keys.filter { it.first == ruleKey }.map { versions.getValue(it) }

        override fun findCurrentVersion(ruleKey: String): RuleVersion? = findByRuleKey(ruleKey).maxByOrNull { it.version }

        override fun findActiveVersion(ruleKey: String): RuleVersion? =
            findByRuleKey(ruleKey)
                .filter {
                    it.status == VersionStatus.ACTIVE
                }.maxByOrNull { it.version }

        override fun findByStatus(
            ruleKey: String,
            status: VersionStatus,
        ): List<RuleVersion> = findByRuleKey(ruleKey).filter { it.status == status }
    }

    private class FakeGrayscaleReleaseRepository : GrayscaleReleaseRepository {
        val releases = mutableMapOf<GrayscaleTarget, GrayscaleRelease>()

        override fun save(release: GrayscaleRelease): GrayscaleRelease {
            releases[release.target] = release
            return release
        }

        override fun findById(id: Long): GrayscaleRelease? = releases.values.firstOrNull { it.id == id }

        override fun findRunningByTarget(target: GrayscaleTarget): GrayscaleRelease? =
            releases[target]?.takeIf {
                it.status ==
                    GrayscaleStatus.RUNNING
            }

        override fun findByTarget(target: GrayscaleTarget): List<GrayscaleRelease> = listOfNotNull(releases[target])

        override fun findByStatus(status: GrayscaleStatus): List<GrayscaleRelease> = releases.values.filter { it.status == status }

        override fun findAll(): List<GrayscaleRelease> = releases.values.toList()
    }

    private class FakeFlowReader : DecisionFlowReader {
        var main: DecisionFlowMain? = null
        val versions = mutableMapOf<Pair<String, Int>, DecisionFlowVersion>()

        override fun findMain(flowKey: String): DecisionFlowMain? = main?.takeIf { it.flowKey == flowKey }

        override fun findVersion(
            flowKey: String,
            version: Int,
        ): DecisionFlowVersion? = versions[flowKey to version]
    }

    private class RecordingMetricsCounter : GrayscaleMetricsCounter {
        val calls = mutableListOf<Triple<Long, Int, Boolean>>()

        override fun increment(
            configId: Long,
            version: Int,
            execTimeMs: Int,
            isSuccess: Boolean,
        ): Int {
            calls.add(Triple(configId, version, isSuccess))
            return 1
        }
    }

    // ---------- 工具 ----------

    /** 每用例独立的 fake 组（@TestInstance(PER_CLASS) 下避免状态串扰） */
    private inner class Ctx {
        val ruleRepo = FakeRuleRepository()
        val versionRepo = FakeRuleVersionRepository()
        val grayscaleRepo = FakeGrayscaleReleaseRepository()
        val flowRepo = FakeFlowReader()
        val metrics = RecordingMetricsCounter()

        fun router(): GrayscaleRouter = GrayscaleRouter(ruleRepo, versionRepo, grayscaleRepo, flowRepo, metrics, DecisionProperties())
    }

    private fun Ctx.seedRule(
        ruleKey: String = "fraud_rule",
        activeVersion: Int? = 1,
        status: RuleStatus = RuleStatus.ENABLED,
    ) {
        ruleRepo.rules[ruleKey] =
            Rule(
                ruleKey = ruleKey,
                ruleName = "规则-$ruleKey",
                status = status,
                currentVersion = 2,
                activeVersion = activeVersion,
                createdBy = "tester",
                createdAt = now,
            )
        versionRepo.save(RuleVersion(ruleKey, 1, """{"v":1}""", VersionStatus.ACTIVE, changedBy = "tester", changedAt = now))
        versionRepo.save(RuleVersion(ruleKey, 2, """{"v":2}""", VersionStatus.CANARY, changedBy = "tester", changedAt = now))
    }

    private fun Ctx.seedRunningGrayscale(
        ruleKey: String,
        grayscaleVersion: Int = 2,
        percentage: Int = 100,
    ) {
        grayscaleRepo.save(
            GrayscaleRelease(
                id = 77L,
                target = GrayscaleTarget(GrayscaleTargetType.RULE, ruleKey),
                currentVersion = 1,
                grayscaleVersion = grayscaleVersion,
                policy = GrayscalePolicy.Percentage(percentage),
                status = GrayscaleStatus.RUNNING,
                createdBy = "tester",
                createdAt = now,
                startedAt = now,
            ),
        )
    }

    // ---------- 版本钉住 ----------

    @Test
    fun `pins active version when no grayscale exists`() {
        val ctx = Ctx()
        ctx.seedRule()
        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(ctx.router().resolveRule("fraud_rule", emptyMap())).snapshot
        assertEquals(1, snapshot.pinnedVersion)
        assertEquals("""{"v":1}""", snapshot.definitionJson)
        assertEquals(false, snapshot.fromCanary)
        assertNull(snapshot.grayscaleConfigId)
    }

    @Test
    fun `pins canary version when grayscale matches`() {
        val ctx = Ctx()
        ctx.seedRule()
        ctx.seedRunningGrayscale("fraud_rule", grayscaleVersion = 2, percentage = 100)
        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(ctx.router().resolveRule("fraud_rule", mapOf("userId" to "u-1"))).snapshot
        assertEquals(2, snapshot.pinnedVersion)
        assertEquals("""{"v":2}""", snapshot.definitionJson)
        assertTrue(snapshot.fromCanary)
        assertEquals(77L, snapshot.grayscaleConfigId)
    }

    @Test
    fun `stays on active version when grayscale percentage is 0`() {
        val ctx = Ctx()
        ctx.seedRule()
        ctx.seedRunningGrayscale("fraud_rule", grayscaleVersion = 2, percentage = 0)
        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(ctx.router().resolveRule("fraud_rule", mapOf("userId" to "u-1"))).snapshot
        assertEquals(1, snapshot.pinnedVersion)
        assertEquals(false, snapshot.fromCanary)
        // 未命中但仍记执行次数（命中当前版本）
        assertEquals(1, ctx.metrics.calls.size)
        assertEquals(77L to 1, ctx.metrics.calls[0].first to ctx.metrics.calls[0].second)
    }

    @Test
    fun `falls back to active version when canary payload is missing`() {
        val ctx = Ctx()
        ctx.seedRule()
        // 灰度指向不存在的版本 9 → 回退生效版本 1
        ctx.seedRunningGrayscale("fraud_rule", grayscaleVersion = 9, percentage = 100)
        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(ctx.router().resolveRule("fraud_rule", mapOf("userId" to "u-1"))).snapshot
        assertEquals(1, snapshot.pinnedVersion)
        assertEquals(false, snapshot.fromCanary)
    }

    @Test
    fun `paused grayscale stops splitting`() {
        val ctx = Ctx()
        ctx.seedRule()
        ctx.seedRunningGrayscale("fraud_rule", percentage = 100)
        // 置为 PAUSED（直接改 fake 存储）
        val paused =
            ctx.grayscaleRepo
                .findRunningByTarget(GrayscaleTarget(GrayscaleTargetType.RULE, "fraud_rule"))!!
                .pause()
        ctx.grayscaleRepo.save(paused)
        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(ctx.router().resolveRule("fraud_rule", mapOf("userId" to "u-1"))).snapshot
        assertEquals(1, snapshot.pinnedVersion)
        assertEquals(false, snapshot.fromCanary)
    }

    @Test
    fun `missing rule and disabled rule produce distinct outcomes`() {
        val ctx = Ctx()
        ctx.seedRule(status = RuleStatus.DISABLED)
        assertIs<RuleSnapshotOutcome.NotFound>(ctx.router().resolveRule("no_such", emptyMap()))
        assertIs<RuleSnapshotOutcome.Disabled>(ctx.router().resolveRule("fraud_rule", emptyMap()))
    }

    @Test
    fun `falls back to current version when main row has no active pointer`() {
        val ctx = Ctx()
        ctx.ruleRepo.rules["legacy_rule"] =
            Rule(
                ruleKey = "legacy_rule",
                ruleName = "历史规则",
                currentVersion = 2,
                activeVersion = null,
                createdBy = "tester",
                createdAt = now,
            )
        ctx.versionRepo.save(RuleVersion("legacy_rule", 1, """{"v":1}""", VersionStatus.ARCHIVED, changedBy = "tester", changedAt = now))
        ctx.versionRepo.save(RuleVersion("legacy_rule", 2, """{"v":2}""", VersionStatus.ACTIVE, changedBy = "tester", changedAt = now))
        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(ctx.router().resolveRule("legacy_rule", emptyMap())).snapshot
        // 主行无指针 → 查 ACTIVE 版本行 → v2
        assertEquals(2, snapshot.pinnedVersion)
    }

    // ---------- 缓存 ----------

    @Test
    fun `second resolution hits caches without repository lookups`() {
        val ctx = Ctx()
        ctx.seedRule()
        val router = ctx.router()
        router.resolveRule("fraud_rule", emptyMap())
        val repoLookupsAfterFirst = ctx.ruleRepo.lookups
        val versionLookupsAfterFirst = ctx.versionRepo.lookups

        val snapshot = assertIs<RuleSnapshotOutcome.Resolved>(router.resolveRule("fraud_rule", emptyMap())).snapshot
        assertEquals(1, snapshot.pinnedVersion)
        assertEquals(repoLookupsAfterFirst, ctx.ruleRepo.lookups, "规则主行应命中缓存，不再查仓储")
        assertEquals(versionLookupsAfterFirst, ctx.versionRepo.lookups, "版本载荷应命中缓存，不再查仓储")
    }

    // ---------- 决策流 ----------

    @Test
    fun `flow pins main version and loads canary graph on hit`() {
        val ctx = Ctx()
        ctx.flowRepo.main =
            DecisionFlowMain(
                id = 1,
                flowKey = "fraud_flow",
                flowName = "流",
                flowDescription = null,
                flowGraph = """{"nodes":[],"edges":[]}""",
                version = 3,
                activeVersion = 3,
                status = "ACTIVE",
                createdBy = "tester",
                createdAt = now,
                updatedBy = null,
                updatedAt = null,
                enabled = true,
                environmentId = null,
            )
        ctx.flowRepo.versions["fraud_flow" to 4] =
            DecisionFlowVersion(
                id = 1,
                flowId = 1,
                flowKey = "fraud_flow",
                version = 4,
                flowGraph = """{"nodes":[{"canary":true}],"edges":[]}""",
                changeReason = null,
                changedBy = "tester",
                changedAt = now,
                isRollback = false,
                rollbackFromVersion = null,
                status = "CANARY",
            )
        ctx.grayscaleRepo.save(
            GrayscaleRelease(
                id = 88L,
                target = GrayscaleTarget(GrayscaleTargetType.DECISION_FLOW, "fraud_flow"),
                currentVersion = 3,
                grayscaleVersion = 4,
                policy = GrayscalePolicy.Percentage(100),
                status = GrayscaleStatus.RUNNING,
                createdBy = "tester",
                createdAt = now,
                startedAt = now,
            ),
        )
        val outcome = ctx.router().resolveFlow("fraud_flow", mapOf("userId" to "u-1"))
        val snapshot = assertIs<com.eatmoreduck.ruleengine.decision.core.FlowSnapshotOutcome.Resolved>(outcome).snapshot
        assertEquals(4, snapshot.pinnedVersion)
        assertTrue(snapshot.fromCanary)
        assertTrue(snapshot.graphJson.contains("canary"))
    }
}
