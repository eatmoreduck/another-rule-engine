package com.example.ruleengine.admin

import com.example.ruleengine.admin.dto.CreateRuleRequest
import com.example.ruleengine.admin.dto.UpdateRuleRequest
import com.example.ruleengine.admin.rules.RuleAssembler
import com.example.ruleengine.admin.rules.RulePayloadValidator
import com.example.ruleengine.admin.rules.RuleService
import com.example.ruleengine.domain.RuleStatus
import com.example.ruleengine.domain.VersionStatus
import com.example.ruleengine.engine.GroovyScriptEngine
import com.example.ruleengine.shared.cache.CacheInvalidationEvent
import com.example.ruleengine.shared.cache.CacheInvalidationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * 规则生命周期服务单元测试（内存仓储，无需数据库/Docker）。
 * 覆盖：CRUD、启停幂等、软删除守卫、定义载荷两道校验链的错误映射。
 */
@DisplayName("规则生命周期服务")
class RuleServiceTest {
    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var versionRepository: FakeRuleVersionRepository
    private lateinit var eventPublisher: RecordingEventPublisher
    private lateinit var service: RuleService

    private val script =
        """
        def amount = features.orderAmount
        return amount > 1000 ? 'REJECT' : 'PASS'
        """.trimIndent()

    @BeforeEach
    fun setUp() {
        ruleRepository = FakeRuleRepository()
        versionRepository = FakeRuleVersionRepository()
        val payloadValidator = RulePayloadValidator(GroovyScriptEngine())
        eventPublisher = RecordingEventPublisher()
        service =
            RuleService(
                ruleRepository = ruleRepository,
                versionRepository = versionRepository,
                payloadValidator = payloadValidator,
                assembler = RuleAssembler(),
                decisionFlowSupportRepository = FakeDecisionFlowSupportRepository(),
                eventPublisher = eventPublisher,
            )
    }

    private fun create(key: String = "rule_a") =
        service.createRule(CreateRuleRequest(ruleKey = key, ruleName = "规则-$key", ruleDescription = "描述", groovyScript = script), "tester")

    @Test
    fun `创建规则落版本 1 ACTIVE 且响应字段完整`() {
        val response = create("rule_create")
        assertNotNull(response.id)
        assertEquals("rule_create", response.ruleKey)
        assertEquals(1, response.version)
        assertEquals(1, response.activeVersion)
        assertTrue(response.enabled)
        assertFalse(response.deleted)
        assertEquals(0L, response.optLockVersion)
        assertEquals(script, response.groovyScript)
        assertEquals("tester", response.createdBy)

        val version = versionRepository.findByRuleKeyAndVersion("rule_create", 1)!!
        assertEquals(VersionStatus.ACTIVE, version.status)

        // 阶段 5：成功变更分支发布决策侧缓存失效事件
        val invalidation = eventPublisher.published<CacheInvalidationEvent>().single()
        assertEquals(CacheInvalidationType.RULE, invalidation.type)
        assertEquals("rule_create", invalidation.key)
        assertEquals("admin-api", invalidation.source)
    }

    @Test
    fun `重复 ruleKey 创建被拒`() {
        create("dup_rule")
        val error =
            assertThrows<IllegalArgumentException> {
                create("dup_rule")
            }
        assertEquals("规则Key已存在: dup_rule", error.message)
    }

    @Test
    fun `危险 Groovy 脚本被安全审计拒绝并映射为旧契约消息`() {
        val error =
            assertThrows<IllegalArgumentException> {
                service.createRule(
                    CreateRuleRequest(ruleKey = "bad_rule", ruleName = "坏规则", groovyScript = "System.exit(1)"),
                    "tester",
                )
            }
        assertTrue(error.message!!.startsWith("Groovy脚本语法错误: "))
        assertTrue(error.message!!.contains("安全审计失败"))
        assertFalse(ruleRepository.existsByRuleKey("bad_rule"))
    }

    @Test
    fun `非法 DSL JSON 被解析层拒绝`() {
        val error =
            assertThrows<IllegalArgumentException> {
                service.createRule(
                    CreateRuleRequest(ruleKey = "bad_json", ruleName = "坏JSON", groovyScript = "{\"rules\": not-json"),
                    "tester",
                )
            }
        assertTrue(error.message!!.startsWith("Groovy脚本语法错误: "))
    }

    @Test
    fun `DSL JSON 结构错误（空逻辑组）被校验层拒绝`() {
        val error =
            assertThrows<IllegalArgumentException> {
                service.createRule(
                    CreateRuleRequest(
                        ruleKey = "bad_dsl",
                        ruleName = "坏结构",
                        groovyScript =
                            """{"defaultAction":"PASS","defaultReason":"ok","rules":[{"id":"r1",
                               "condition":{"id":"c1","type":"group","logic":"AND","children":[]},
                               "action":"REJECT","reason":"hit"}]}""",
                    ),
                    "tester",
                )
            }
        assertTrue(error.message!!.contains("空逻辑组"))
    }

    @Test
    fun `仅元数据更新不创建新版本`() {
        create("meta_rule")
        val updated =
            service.updateRule(
                "meta_rule",
                UpdateRuleRequest(ruleName = "新名称", ruleDescription = "新描述"),
                "editor",
            )
        assertEquals("新名称", updated.ruleName)
        assertEquals(1, updated.version)
        assertEquals(1, versionRepository.findByRuleKey("meta_rule").size)
    }

    @Test
    fun `脚本变更创建 DRAFT 新版本并推进主表版本号`() {
        create("ver_rule")
        val updated =
            service.updateRule(
                "ver_rule",
                UpdateRuleRequest(groovyScript = "return 'PASS'", changeReason = "放宽阈值"),
                "editor",
            )
        assertEquals(2, updated.version)
        assertEquals("return 'PASS'", updated.groovyScript)
        assertEquals(VersionStatus.DRAFT, versionRepository.findByRuleKeyAndVersion("ver_rule", 2)!!.status)
        // 生效版本仍是 v1（旧观感由灰度全量切换推进）
        assertEquals(1, updated.activeVersion)
    }

    @Test
    fun `软删除后再次删除被守卫拒绝`() {
        create("del_rule")
        service.deleteRule("del_rule", "tester")
        assertEquals(RuleStatus.DELETED, ruleRepository.findByRuleKey("del_rule")!!.status)
        val error =
            assertThrows<IllegalStateException> {
                service.deleteRule("del_rule", "tester")
            }
        assertEquals("规则正在使用中，不能删除: del_rule", error.message)
    }

    @Test
    fun `启停幂等且已删除规则禁止再启用`() {
        create("toggle_rule")
        val disabled = service.disableRule("toggle_rule", "tester")
        assertFalse(disabled.enabled)
        // 幂等：重复禁用直接返回
        val disabledAgain = service.disableRule("toggle_rule", "tester")
        assertFalse(disabledAgain.enabled)
        val enabled = service.enableRule("toggle_rule", "tester")
        assertTrue(enabled.enabled)

        ruleRepository.save(ruleRepository.findByRuleKey("toggle_rule")!!.softDelete("tester", Instant.now()))
        val error =
            assertThrows<IllegalArgumentException> {
                service.enableRule("toggle_rule", "tester")
            }
        assertTrue(error.message!!.contains("不允许的状态迁移"))
    }

    @Test
    fun `查询不存在的规则返回旧契约消息`() {
        val error =
            assertThrows<IllegalArgumentException> {
                service.getRule("missing_rule")
            }
        assertEquals("规则不存在: missing_rule", error.message)
    }

    @Test
    fun `列表默认排除已删除且支持关键字与启用过滤`() {
        create("list_rule_1")
        create("list_rule_2")
        service.disableRule("list_rule_2", "tester")
        service.deleteRule("list_rule_2", "tester")

        assertEquals(1, service.listRules(0, 20, showDeleted = false, keyword = null, enabled = null).totalElements)
        assertEquals(2, service.listRules(0, 20, showDeleted = true, keyword = null, enabled = null).totalElements)
        assertEquals(1, service.listRules(0, 20, showDeleted = true, keyword = null, enabled = true).totalElements)
        assertEquals(1, service.listRules(0, 20, showDeleted = false, keyword = "list_rule_1", enabled = null).totalElements)
    }
}
