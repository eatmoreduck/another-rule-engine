package com.example.ruleengine.admin

import com.example.ruleengine.admin.dto.CreateGrayscaleRequest
import com.example.ruleengine.admin.dto.CreateRuleRequest
import com.example.ruleengine.admin.dto.CreateVersionRequest
import com.example.ruleengine.admin.grayscale.GrayscaleAssembler
import com.example.ruleengine.admin.grayscale.GrayscaleService
import com.example.ruleengine.admin.rules.RuleAssembler
import com.example.ruleengine.admin.rules.RulePayloadValidator
import com.example.ruleengine.admin.rules.RuleService
import com.example.ruleengine.domain.GrayscalePolicy
import com.example.ruleengine.domain.GrayscaleStatus
import com.example.ruleengine.domain.VersionStatus
import com.example.ruleengine.engine.GroovyScriptEngine
import com.example.ruleengine.shared.cache.CacheInvalidationEvent
import com.example.ruleengine.shared.cache.CacheInvalidationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 灰度发布服务单元测试（内存仓储）。
 * 覆盖：策略参数 fail-fast、状态机守卫消息、CANARY 真正写入（阶段 1 修正）、
 * 全量切换的发布守卫与归档、进度列的呈现语义（COMPLETED=100 / ROLLED_BACK=0）。
 */
@DisplayName("灰度发布服务")
class GrayscaleServiceTest {
    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var versionRepository: FakeRuleVersionRepository
    private lateinit var releaseRepository: FakeGrayscaleReleaseRepository
    private lateinit var flowSupport: FakeDecisionFlowSupportRepository
    private lateinit var metricsRepository: FakeGrayscaleMetricsRepository
    private lateinit var grayscaleService: GrayscaleService
    private lateinit var eventPublisher: RecordingEventPublisher
    private lateinit var versionCreateService: com.example.ruleengine.admin.rules.VersionService

    private val ruleKey = "gray_rule"

    @BeforeEach
    fun setUp() {
        ruleRepository = FakeRuleRepository()
        versionRepository = FakeRuleVersionRepository()
        releaseRepository = FakeGrayscaleReleaseRepository()
        flowSupport = FakeDecisionFlowSupportRepository()
        metricsRepository = FakeGrayscaleMetricsRepository()
        val payloadValidator = RulePayloadValidator(GroovyScriptEngine())
        val ruleService =
            RuleService(ruleRepository, versionRepository, payloadValidator, RuleAssembler(), flowSupport, RecordingEventPublisher())
        versionCreateService =
            com.example.ruleengine.admin.rules
                .VersionService(ruleRepository, versionRepository, payloadValidator, RuleAssembler(), RecordingEventPublisher())
        eventPublisher = RecordingEventPublisher()
        grayscaleService =
            GrayscaleService(
                releaseRepository,
                ruleRepository,
                versionRepository,
                flowSupport,
                metricsRepository,
                GrayscaleAssembler(),
                eventPublisher,
            )

        // 准备：v1 ACTIVE（创建即生效）+ v2 DRAFT（灰度候选）
        ruleService.createRule(CreateRuleRequest(ruleKey = ruleKey, ruleName = "灰度规则", groovyScript = "return 'PASS'"), "tester")
        versionCreateService.createVersion(ruleKey, CreateVersionRequest(groovyScript = "return 'REJECT'"), "tester")
    }

    private fun createRequest(
        version: Int = 2,
        percentage: Int = 50,
    ): CreateGrayscaleRequest =
        CreateGrayscaleRequest(
            ruleKey = ruleKey,
            targetType = "RULE",
            targetKey = ruleKey,
            grayscaleVersion = version,
            grayscalePercentage = percentage,
        )

    @Test
    fun `创建灰度配置为 DRAFT 且初始化两个版本的零值指标`() {
        val response = grayscaleService.createGrayscaleConfig(createRequest(), "operator")

        assertEquals(GrayscaleStatus.DRAFT.name, response.status)
        assertEquals("草稿", response.statusDescription)
        assertEquals(1, response.currentVersion)
        assertEquals(2, response.grayscaleVersion)
        assertEquals(50, response.grayscalePercentage)
        assertEquals("PERCENTAGE", response.strategyType)
        assertEquals(ruleKey, response.ruleKey)

        val configId = response.id!!
        assertEquals(
            setOf(1, 2),
            metricsRepository.findByConfigId(configId).map { it.version }.toSet(),
        )
    }

    @Test
    fun `灰度版本与现行版本相同时被领域不变式拒绝`() {
        val error =
            assertThrows<IllegalArgumentException> {
                grayscaleService.createGrayscaleConfig(createRequest(version = 1), "operator")
            }
        assertTrue(error.message!!.contains("灰度版本(1)不得与现行版本(1)相同"))
    }

    @Test
    fun `灰度版本不存在时拒绝`() {
        val error =
            assertThrows<IllegalArgumentException> {
                grayscaleService.createGrayscaleConfig(createRequest(version = 9), "operator")
            }
        assertEquals("灰度版本不存在: $ruleKey version=9", error.message)
    }

    @Test
    fun `同一目标已有运行中灰度时拒绝重复创建`() {
        grayscaleService.createGrayscaleConfig(createRequest(), "operator")
        val configId = releaseRepository.findAll().first().id!!
        grayscaleService.startGrayscale(configId)

        val error =
            assertThrows<IllegalArgumentException> {
                grayscaleService.createGrayscaleConfig(createRequest(), "operator")
            }
        assertEquals("规则已有运行中的灰度配置: $ruleKey", error.message)
    }

    @Test
    fun `WHITELIST 空名单在创建时被拒绝（fail-fast 修正）`() {
        val error =
            assertThrows<IllegalArgumentException> {
                grayscaleService.createGrayscaleConfig(
                    createRequest().copy(strategyType = "WHITELIST", whitelistIds = " , "),
                    "operator",
                )
            }
        assertTrue(error.message!!.contains("白名单不能为空"))
    }

    @Test
    fun `FEATURE 策略缺规则或 JSON 损坏在创建时被拒绝`() {
        val missing =
            assertThrows<IllegalArgumentException> {
                grayscaleService.createGrayscaleConfig(createRequest().copy(strategyType = "FEATURE"), "operator")
            }
        assertTrue(missing.message!!.contains("特征匹配规则不能为空"))

        val broken =
            assertThrows<IllegalArgumentException> {
                grayscaleService.createGrayscaleConfig(
                    createRequest().copy(strategyType = "FEATURE", featureRules = "[{\"field\":\"region\"}]"),
                    "operator",
                )
            }
        assertTrue(broken.message!!.contains("feature_rules"))
    }

    @Test
    fun `启动灰度推进状态并把灰度版本写入 CANARY（阶段 1 修正）`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!

        val started = grayscaleService.startGrayscale(configId)

        assertEquals(GrayscaleStatus.RUNNING.name, started.status)
        assertEquals("运行中", started.statusDescription)
        assertNotNull(started.startedAt)
        assertNull(started.completedAt)
        assertEquals(
            VersionStatus.CANARY,
            versionRepository.findByRuleKeyAndVersion(ruleKey, 2)!!.status,
        )

        // 阶段 5：灰度状态变更广播决策侧失效（创建 + 启动各一则 GRAYSCALE 事件）
        val invalidations = eventPublisher.published<CacheInvalidationEvent>()
        assertEquals(2, invalidations.size)
        assertTrue(invalidations.all { it.type == CacheInvalidationType.GRAYSCALE && it.key == ruleKey })

        // 幂等保护：再次启动被守卫拒绝
        val error =
            assertThrows<IllegalArgumentException> {
                grayscaleService.startGrayscale(configId)
            }
        assertEquals("只有草稿或已暂停状态的灰度配置才能启动，当前状态: 运行中", error.message)
    }

    @Test
    fun `全量切换经发布守卫：灰度版本 ACTIVE、旧版本归档、主表指针推进`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!
        grayscaleService.startGrayscale(configId)

        val completed = grayscaleService.completeGrayscale(configId)

        assertEquals(GrayscaleStatus.COMPLETED.name, completed.status)
        assertEquals("已完成", completed.statusDescription)
        assertEquals(100, completed.grayscalePercentage)
        assertNotNull(completed.completedAt)
        assertEquals(
            VersionStatus.ACTIVE,
            versionRepository.findByRuleKeyAndVersion(ruleKey, 2)!!.status,
        )
        assertEquals(
            VersionStatus.ARCHIVED,
            versionRepository.findByRuleKeyAndVersion(ruleKey, 1)!!.status,
        )
        val rule = ruleRepository.findByRuleKey(ruleKey)!!
        assertEquals(2, rule.activeVersion)
        assertEquals(2, rule.currentVersion)

        // 终态守卫
        val restartError =
            assertThrows<IllegalArgumentException> { grayscaleService.startGrayscale(configId) }
        assertEquals("只有草稿或已暂停状态的灰度配置才能启动，当前状态: 已完成", restartError.message)
        val rollbackError =
            assertThrows<IllegalArgumentException> { grayscaleService.rollbackGrayscale(configId) }
        assertEquals("已完成或已回滚的灰度配置不能再次回滚，当前状态: 已完成", rollbackError.message)
    }

    @Test
    fun `未启动的灰度可暂停前回滚且进度回显为 0`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!

        val rolled = grayscaleService.rollbackGrayscale(configId)

        assertEquals(GrayscaleStatus.ROLLED_BACK.name, rolled.status)
        assertEquals("已回滚", rolled.statusDescription)
        assertEquals(0, rolled.grayscalePercentage)
        // 回滚不影响版本状态（规则内容未被切换过）
        assertEquals(
            VersionStatus.DRAFT,
            versionRepository.findByRuleKeyAndVersion(ruleKey, 2)!!.status,
        )
    }

    @Test
    fun `暂停与恢复循环`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!
        grayscaleService.startGrayscale(configId)

        val paused = grayscaleService.pauseGrayscale(configId)
        assertEquals(GrayscaleStatus.PAUSED.name, paused.status)
        assertEquals("已暂停", paused.statusDescription)
        // RUNNING→PAUSED 后灰度版本保持 CANARY
        assertEquals(
            VersionStatus.CANARY,
            versionRepository.findByRuleKeyAndVersion(ruleKey, 2)!!.status,
        )
        // PAUSED 可恢复启动
        val resumed = grayscaleService.startGrayscale(configId)
        assertEquals(GrayscaleStatus.RUNNING.name, resumed.status)
    }

    @Test
    fun `暂停前不可完成以外的操作守卫消息`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!
        val error =
            assertThrows<IllegalArgumentException> { grayscaleService.pauseGrayscale(configId) }
        assertEquals("只有运行中的灰度配置才能暂停，当前状态: 草稿", error.message)
    }

    @Test
    fun `对比报告返回零值指标结构`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!
        val report = grayscaleService.getGrayscaleReport(configId)

        assertEquals(configId, report.configId)
        assertEquals(1, report.currentVersion)
        assertEquals(2, report.grayscaleVersion)
        assertEquals(0, report.currentVersionMetrics!!.executionCount)
        assertEquals(0.0, report.currentVersionMetrics!!.hitRate)
        assertEquals(0.0, report.grayscaleVersionMetrics!!.errorRate)
    }

    @Test
    fun `列表过滤与规则灰度历史`() {
        val configId = grayscaleService.createGrayscaleConfig(createRequest(), "operator").id!!
        assertEquals(1, grayscaleService.listGrayscaleConfigs(status = null, ruleKey = ruleKey, targetType = null).size)
        assertEquals(
            1,
            grayscaleService.listGrayscaleConfigs(status = "DRAFT", ruleKey = null, targetType = "RULE").size,
        )
        assertTrue(grayscaleService.listGrayscaleConfigs(status = "RUNNING", ruleKey = null, targetType = null).isEmpty())
        assertEquals(1, grayscaleService.getGrayscaleConfigs(ruleKey).size)

        val unknownStatus =
            assertThrows<IllegalArgumentException> {
                grayscaleService.listGrayscaleConfigs(status = "NOPE", ruleKey = null, targetType = null)
            }
        assertTrue(unknownStatus.message!!.contains("未知的灰度状态"))
    }

    @Test
    fun `WHITELIST 策略完整落库并回显逗号串`() {
        val response =
            grayscaleService.createGrayscaleConfig(
                createRequest().copy(strategyType = "WHITELIST", whitelistIds = "u2,u1"),
                "operator",
            )
        assertEquals("WHITELIST", response.strategyType)
        // 回显排序（与存储编码一致）
        assertEquals("u1,u2", response.whitelistIds)
        assertEquals(0, response.grayscalePercentage)
        val saved = releaseRepository.findById(response.id!!)!!
        assertEquals(GrayscalePolicy.Whitelist(setOf("u1", "u2")), saved.policy)
    }

    @Test
    fun `FEATURE 策略完整落库并回显旧 JSON 形状`() {
        val response =
            grayscaleService.createGrayscaleConfig(
                createRequest().copy(
                    strategyType = "FEATURE",
                    featureRules = """[{"field":"region","operator":"EQ","value":"US"}]""",
                ),
                "operator",
            )
        assertEquals("FEATURE", response.strategyType)
        assertEquals("""[{"field":"region","operator":"EQ","value":"US"}]""", response.featureRules)
        val saved = releaseRepository.findById(response.id!!)!!
        assertTrue(saved.policy is GrayscalePolicy.Feature)
    }
}
