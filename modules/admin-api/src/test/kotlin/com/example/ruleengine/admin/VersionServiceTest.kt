package com.example.ruleengine.admin

import com.example.ruleengine.admin.dto.CreateVersionRequest
import com.example.ruleengine.admin.dto.RollbackRequest
import com.example.ruleengine.admin.rules.RuleAssembler
import com.example.ruleengine.admin.rules.RulePayloadValidator
import com.example.ruleengine.admin.rules.RuleService
import com.example.ruleengine.admin.rules.VersionService
import com.example.ruleengine.domain.VersionStatus
import com.example.ruleengine.engine.GroovyScriptEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 版本管理服务单元测试（内存仓储）。
 * 覆盖：版本创建守卫、回滚语义修正（更高版本号 + rollbackFromVersion=目标版本 + 立即发布生效）、
 * 版本对比、TOCTOU 防御。
 */
@DisplayName("规则版本管理服务")
class VersionServiceTest {
    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var versionRepository: FakeRuleVersionRepository
    private lateinit var versionService: VersionService
    private lateinit var ruleService: RuleService

    private val scriptV1 = "return 'PASS'"

    @BeforeEach
    fun setUp() {
        ruleRepository = FakeRuleRepository()
        versionRepository = FakeRuleVersionRepository()
        val payloadValidator = RulePayloadValidator(GroovyScriptEngine())
        ruleService =
            RuleService(ruleRepository, versionRepository, payloadValidator, RuleAssembler(), FakeDecisionFlowSupportRepository())
        versionService = VersionService(ruleRepository, versionRepository, payloadValidator, RuleAssembler())

        ruleService.createRule(
            com.example.ruleengine.admin.dto.CreateRuleRequest(
                ruleKey = "ver_flow",
                ruleName = "版本流程",
                groovyScript = scriptV1,
            ),
            "tester",
        )
    }

    @Test
    fun `创建新版本为 DRAFT 且推进主表版本号`() {
        val response =
            versionService.createVersion("ver_flow", CreateVersionRequest(groovyScript = "return 'REJECT'", changeReason = "收紧"), "editor")

        assertEquals(2, response.version)
        assertEquals("DRAFT", response.status)
        assertEquals("return 'REJECT'", response.groovyScript)
        assertEquals(VersionStatus.DRAFT, versionRepository.findByRuleKeyAndVersion("ver_flow", 2)!!.status)
        assertEquals(2, ruleRepository.findByRuleKey("ver_flow")!!.currentVersion)
    }

    @Test
    fun `新版本号冲突的 TOCTOU 防御`() {
        // 模拟并发写入：在服务推进主表版本号之前，目标版本号已被抢占占位
        versionRepository.save(
            com.example.ruleengine.domain.RuleVersion(
                ruleKey = "ver_flow",
                version = 2,
                definitionJson = "return 'RACE'",
                changeReason = "并发写入",
                changedBy = "racer",
                changedAt = java.time.Instant.now(),
            ),
        )
        val error =
            assertThrows<IllegalArgumentException> {
                versionService.createVersion("ver_flow", CreateVersionRequest(groovyScript = "return 'REJECT'"), "editor2")
            }
        assertTrue(error.message!!.contains("版本冲突"))
    }

    @Test
    fun `回滚以更高版本号落地目标内容并立即发布`() {
        versionService.createVersion("ver_flow", CreateVersionRequest(groovyScript = "return 'REJECT'", changeReason = "收紧"), "editor")

        val rolled =
            versionService.rollbackToVersion("ver_flow", 1, RollbackRequest(targetVersion = 1, reason = "回滚验证"), "operator")

        // 回滚产物：v3、内容 = v1、isRollback、rollbackFromVersion = 目标版本号 1（阶段 1 修正）
        assertEquals(3, rolled.version)
        assertEquals("ACTIVE", rolled.status)
        assertTrue(rolled.isRollback)
        assertEquals(1, rolled.rollbackFromVersion)
        assertEquals(scriptV1, rolled.groovyScript)

        // 旧 ACTIVE（v1）归档，未发布的 v2 保持 DRAFT，v3 成为唯一 ACTIVE
        assertEquals(listOf(3), versionRepository.findByStatus("ver_flow", VersionStatus.ACTIVE).map { it.version })
        assertTrue(versionRepository.findByStatus("ver_flow", VersionStatus.ARCHIVED).any { it.version == 1 })
        assertEquals(
            VersionStatus.DRAFT,
            versionRepository.findByRuleKeyAndVersion("ver_flow", 2)!!.status,
        )
        // 主表指针推进
        val rule = ruleRepository.findByRuleKey("ver_flow")!!
        assertEquals(3, rule.currentVersion)
        assertEquals(3, rule.activeVersion)
    }

    @Test
    fun `回滚目标版本不存在时拒绝`() {
        val error =
            assertThrows<IllegalArgumentException> {
                versionService.rollbackToVersion("ver_flow", 9, RollbackRequest(targetVersion = 9), "operator")
            }
        assertEquals("目标版本不存在: 9", error.message)
    }

    @Test
    fun `版本列表按版本号降序且未知规则返回空`() {
        versionService.createVersion("ver_flow", CreateVersionRequest(groovyScript = "return 'REJECT'"), "editor")
        assertEquals(listOf(2, 1), versionService.getVersions("ver_flow").map { it.version })
        assertTrue(versionService.getVersions("unknown_rule").isEmpty())
    }

    @Test
    fun `特定版本不存在时返回旧契约消息`() {
        val error =
            assertThrows<IllegalArgumentException> {
                versionService.getVersion("ver_flow", 42)
            }
        assertEquals("版本不存在: ruleKey=ver_flow, version=42", error.message)
    }

    @Test
    fun `版本对比产出旧格式的差异文本`() {
        versionService.createVersion("ver_flow", CreateVersionRequest(groovyScript = "return 'REJECT'"), "editor")
        val diff = versionService.compareVersions("ver_flow", 1, 2)
        assertEquals("ver_flow", diff.ruleKey)
        assertEquals(scriptV1, diff.script1)
        assertEquals("return 'REJECT'", diff.script2)
        assertTrue(diff.diff.startsWith("首次差异出现在位置"))

        val same = versionService.compareVersions("ver_flow", 1, 1)
        assertEquals("两个版本内容完全相同", same.diff)
    }
}
