package com.eatmoreduck.ruleengine.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 版本状态机与 RuleVersion 不变式测试。
 */
class VersionLifecycleTest {
    private val now: Instant = Instant.parse("2026-04-10T00:00:00Z")

    private fun version(
        status: VersionStatus = VersionStatus.DRAFT,
        isRollback: Boolean = false,
        rollbackFromVersion: Int? = null,
    ): RuleVersion =
        RuleVersion(
            ruleKey = "anti_fraud_high_amount",
            version = 2,
            definitionJson = """{"type":"script","body":"return true"}""",
            status = status,
            changedBy = "alice",
            changedAt = now,
            isRollback = isRollback,
            rollbackFromVersion = rollbackFromVersion,
        )

    // ---------- 状态机：合法迁移 ----------

    @Test
    fun `DRAFT can transition to CANARY`() {
        // 草稿进入灰度验证
        assertEquals(VersionStatus.CANARY, version().startCanary().status)
    }

    @Test
    fun `DRAFT can transition to ACTIVE`() {
        // 草稿直接发布
        assertEquals(VersionStatus.ACTIVE, version().publish().status)
    }

    @Test
    fun `CANARY can transition to ACTIVE`() {
        // 灰度验证通过后发布（旧守卫：DRAFT 或 CANARY 可发布）
        assertEquals(VersionStatus.ACTIVE, version(status = VersionStatus.CANARY).publish().status)
    }

    @Test
    fun `ACTIVE can transition to ARCHIVED`() {
        // 被新版本替代后归档
        assertEquals(VersionStatus.ARCHIVED, version(status = VersionStatus.ACTIVE).archive().status)
    }

    @Test
    fun `rollback version follows the same lifecycle`() {
        // 回滚落地的新版本（isRollback=true）与普通版本走同一生命周期
        val rollback = version(isRollback = true, rollbackFromVersion = 1)
        assertEquals(VersionStatus.ACTIVE, rollback.publish().status)
    }

    // ---------- 状态机：非法迁移 ----------

    @Test
    fun `ARCHIVED is terminal`() {
        // 归档为终态：发布、再进灰度、再归档均非法
        val archived = version(status = VersionStatus.ARCHIVED)
        assertFailsWith<IllegalTransitionException> { archived.publish() }
        assertFailsWith<IllegalTransitionException> { archived.startCanary() }
        assertFailsWith<IllegalTransitionException> { archived.archive() }
    }

    @Test
    fun `CANARY cannot skip back to DRAFT or jump to ARCHIVED`() {
        // 灰度中的版本只能走向 ACTIVE；放弃灰度属于旧模型缺口（见设计取舍），不在合法边内
        assertFalse(VersionStatus.CANARY.canTransitionTo(VersionStatus.DRAFT))
        assertFalse(VersionStatus.CANARY.canTransitionTo(VersionStatus.ARCHIVED))
        val canary = version(status = VersionStatus.CANARY)
        assertFailsWith<IllegalTransitionException> { canary.archive() }
    }

    @Test
    fun `ACTIVE cannot restart lifecycle`() {
        // 生效版本不可退回草稿或灰度，只能被替代归档
        val active = version(status = VersionStatus.ACTIVE)
        assertFailsWith<IllegalTransitionException> { active.startCanary() }
        assertFailsWith<IllegalTransitionException> { active.publish() }
    }

    // ---------- 发布守卫 ----------

    @Test
    fun `only DRAFT and CANARY are publishable`() {
        // 对应旧 publishVersion 守卫"只有草稿或灰度中的版本才能发布"
        assertTrue(VersionStatus.DRAFT.publishable)
        assertTrue(VersionStatus.CANARY.publishable)
        assertFalse(VersionStatus.ACTIVE.publishable)
        assertFalse(VersionStatus.ARCHIVED.publishable)
    }

    // ---------- 不变式失败路径 ----------
    // 注意：Kotlin data class 的 copy 不经过 init 块，不变式必须用直接构造验证

    private fun invalidVersion(
        version: Int = 2,
        definitionJson: String = """{"type":"script","body":"return true"}""",
        isRollback: Boolean = false,
        rollbackFromVersion: Int? = null,
    ): RuleVersion =
        RuleVersion(
            ruleKey = "anti_fraud_high_amount",
            version = version,
            definitionJson = definitionJson,
            changedBy = "alice",
            changedAt = now,
            isRollback = isRollback,
            rollbackFromVersion = rollbackFromVersion,
        )

    @Test
    fun `non-positive version is rejected`() {
        // 版本号从 1 起
        assertFailsWith<IllegalArgumentException> { invalidVersion(version = 0) }
    }

    @Test
    fun `blank definitionJson is rejected`() {
        // 定义载荷为空是配置错误，fail fast
        assertFailsWith<IllegalArgumentException> { invalidVersion(definitionJson = "  ") }
    }

    @Test
    fun `rollback version without rollbackFromVersion is rejected`() {
        // 回滚版本必须记录来源版本
        assertFailsWith<IllegalArgumentException> { invalidVersion(isRollback = true, rollbackFromVersion = null) }
    }

    @Test
    fun `rollbackFromVersion equal to own version is rejected`() {
        // 回滚来源不可能等于自身（回滚落地的是更高版本号副本）
        assertFailsWith<IllegalArgumentException> { invalidVersion(isRollback = true, rollbackFromVersion = 2) }
    }

    @Test
    fun `non-rollback version carrying rollbackFromVersion is rejected`() {
        // 非回滚版本不允许携带回滚来源字段，防止脏数据
        assertFailsWith<IllegalArgumentException> { invalidVersion(isRollback = false, rollbackFromVersion = 1) }
    }

    @Test
    fun `normal version keeps rollbackFromVersion null`() {
        // 普通版本不携带回滚来源
        assertNull(version().rollbackFromVersion)
        assertFalse(version().isRollback)
    }

    // ---------- 值语义 ----------

    @Test
    fun `versions with same fields are equal`() {
        // data class 等价性：同字段的两个版本实例相等
        assertEquals(version(), version())
    }
}
