package com.eatmoreduck.ruleengine.admin

import com.eatmoreduck.ruleengine.admin.environment.EnvironmentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * 环境管理服务单元测试（内存仓储）。
 * 覆盖：环境列表/详情、环境下规则查询、克隆的跳过/覆盖/跨环境同键跳过语义、
 * 未知环境守卫（消息与旧实现一致）。
 */
@DisplayName("环境管理服务")
class EnvironmentServiceTest {
    private lateinit var environmentRepository: FakeEnvironmentRepository
    private lateinit var ruleSupportRepository: FakeEnvironmentRuleSupportRepository
    private lateinit var service: EnvironmentService

    private var devId: Long = 0
    private var stagingId: Long = 0

    @BeforeEach
    fun setUp() {
        environmentRepository = FakeEnvironmentRepository()
        ruleSupportRepository = FakeEnvironmentRuleSupportRepository()
        service = EnvironmentService(environmentRepository, ruleSupportRepository)
        devId = environmentRepository.seed("DEV", "DEV").id
        stagingId = environmentRepository.seed("STAGING", "STAGING").id
    }

    private fun rule(
        ruleKey: String,
        environmentId: Long?,
        script: String = "return 'PASS'",
    ) = ruleSupportRepository.seed(
        com.eatmoreduck.ruleengine.admin.environment.EnvironmentRuleRow(
            id = 0L,
            ruleKey = ruleKey,
            ruleName = "$ruleKey 规则",
            ruleDescription = null,
            groovyScript = script,
            version = 1,
            enabled = true,
            deleted = false,
            createdBy = "tester",
            createdAt = Instant.now(),
            updatedBy = null,
            updatedAt = null,
            environmentId = environmentId,
        ),
    )

    @Test
    fun `环境列表与详情`() {
        assertEquals(2, service.listEnvironments().size)
        assertEquals("DEV", service.listEnvironments()[0].name)

        val detail = service.getEnvironment(devId)
        assertEquals("DEV", detail.name)
        assertEquals("DEV", detail.type)

        val missing = assertThrows<IllegalArgumentException> { service.getEnvironment(999L) }
        assertEquals("环境不存在: 999", missing.message)
    }

    @Test
    fun `环境下规则查询返回主表列值`() {
        rule("r_in_dev", devId, script = "return 'REJECT'")
        rule("r_no_env", null)

        val rules = service.getRulesByEnvironment(devId)
        assertEquals(1, rules.size)
        assertEquals("r_in_dev", rules[0].ruleKey)
        assertEquals("return 'REJECT'", rules[0].groovyScript)

        val missing = assertThrows<IllegalArgumentException> { service.getRulesByEnvironment(999L) }
        assertEquals("环境不存在: 999", missing.message)
    }

    @Test
    fun `克隆按名称解析环境且未知环境拒绝`() {
        val from =
            assertThrows<IllegalArgumentException> {
                service.cloneEnvironmentRules("GHOST", "STAGING", overwrite = false, operator = "op")
            }
        assertEquals("环境不存在: GHOST", from.message)

        val to =
            assertThrows<IllegalArgumentException> {
                service.cloneEnvironmentRules("DEV", "GHOST", overwrite = false, operator = "op")
            }
        assertEquals("环境不存在: GHOST", to.message)
    }

    @Test
    fun `克隆源环境为空时复制零条`() {
        val response = service.cloneEnvironmentRules("DEV", "STAGING", overwrite = false, operator = "op")

        assertTrue(response.success)
        assertEquals(0, response.clonedCount)
        assertEquals(0, response.skippedCount)
        assertEquals("克隆完成: 复制 0 条规则, 跳过 0 条", response.message)
    }

    @Test
    fun `克隆键被全局占用时计为跳过而非约束爆炸`() {
        // 源环境 2 条规则：rules.rule_key 全局唯一 → 源行自身即占用键，跨环境复制必然违规，
        // 服务层在写入前探测并计为跳过（旧实现此处直接 500）
        rule("r_a", devId)
        rule("r_b", devId)

        val response = service.cloneEnvironmentRules("DEV", "STAGING", overwrite = false, operator = "op")

        assertTrue(response.success)
        assertEquals(0, response.clonedCount)
        assertEquals(2, response.skippedCount)
        assertEquals(0, ruleSupportRepository.findByEnvironmentId(stagingId).size)
    }

    @Test
    fun `克隆非覆盖模式跳过目标已有同名键`() {
        // 注：内存仓储允许构造真实唯一约束不容的同键两行，
        // 用于检验旧契约的"目标已命中优先"分支（服务层判定顺序：目标命中 → 全局占用）
        rule("r_dup", devId)
        rule("r_dup", stagingId)

        val response = service.cloneEnvironmentRules("DEV", "STAGING", overwrite = false, operator = "op")

        assertEquals(0, response.clonedCount)
        assertEquals(1, response.skippedCount)
    }

    @Test
    fun `克隆覆盖模式更新目标同名规则内容`() {
        // 同上：覆盖分支为旧契约保留（真实 schema 下目标与源同键不可共存）
        rule("r_dup", devId, script = "return 'NEW'")
        val target = rule("r_dup", stagingId, script = "return 'OLD'")

        val response = service.cloneEnvironmentRules("DEV", "STAGING", overwrite = true, operator = "op")

        assertEquals(1, response.clonedCount)
        assertEquals(0, response.skippedCount)
        assertEquals("return 'NEW'", ruleSupportRepository.rules[target.id]!!.groovyScript)
        assertEquals("op", ruleSupportRepository.rules[target.id]!!.updatedBy)
        // 不新增行
        assertEquals(1, ruleSupportRepository.findByEnvironmentId(stagingId).size)
    }

    @Test
    fun `克隆排除无环境规则且软删规则照旧参与`() {
        rule("r_no_env", null)
        val deleted = rule("r_deleted", devId)
        ruleSupportRepository.rules[deleted.id] = deleted.copy(deleted = true)

        val response = service.cloneEnvironmentRules("DEV", "STAGING", overwrite = false, operator = "op")
        // 软删行也参与克隆（照旧 findByEnvironmentId 不过滤）→ 计入跳过；无环境行不参与
        assertEquals(0, response.clonedCount)
        assertEquals(1, response.skippedCount)
        assertEquals(0, ruleSupportRepository.findByEnvironmentId(stagingId).size)
    }
}
