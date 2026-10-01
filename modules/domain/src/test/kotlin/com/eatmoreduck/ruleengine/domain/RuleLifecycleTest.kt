package com.eatmoreduck.ruleengine.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 规则生命周期与聚合不变式测试。
 */
class RuleLifecycleTest {
    private val now: java.time.Instant = java.time.Instant.parse("2026-04-10T00:00:00Z")

    private fun rule(
        status: RuleStatus = RuleStatus.ENABLED,
        currentVersion: Int = 1,
        activeVersion: Int? = 1,
    ): Rule =
        Rule(
            ruleKey = "anti_fraud_high_amount",
            ruleName = "大额订单风控",
            status = status,
            currentVersion = currentVersion,
            activeVersion = activeVersion,
            createdBy = "alice",
            createdAt = now,
        )

    // ---------- 状态机：合法迁移 ----------

    @Test
    fun `ENABLED can transition to DISABLED`() {
        // 启用中的规则允许停用（对应旧 RULE_DISABLE）
        val disabled = rule().disable("bob", now)
        assertEquals(RuleStatus.DISABLED, disabled.status)
    }

    @Test
    fun `DISABLED can transition to ENABLED`() {
        // 停用的规则允许重新启用（对应旧 RULE_ENABLE）
        val enabled = rule(status = RuleStatus.DISABLED).enable("bob", now)
        assertEquals(RuleStatus.ENABLED, enabled.status)
    }

    @Test
    fun `ENABLED can transition to DELETED`() {
        // 启用中的规则允许软删除
        assertEquals(RuleStatus.DELETED, rule().softDelete("bob", now).status)
    }

    @Test
    fun `DISABLED can transition to DELETED`() {
        // 停用的规则同样允许软删除
        assertEquals(RuleStatus.DELETED, rule(status = RuleStatus.DISABLED).softDelete("bob", now).status)
    }

    // ---------- 状态机：非法迁移 ----------

    @Test
    fun `DELETED is terminal and rejects any transition`() {
        // 软删除为终态：禁用、启用、再次删除均非法，必须显式抛异常而非静默
        val deleted = rule(status = RuleStatus.DELETED)
        assertFailsWith<IllegalTransitionException> { deleted.disable("bob", now) }
        assertFailsWith<IllegalTransitionException> { deleted.enable("bob", now) }
        assertFailsWith<IllegalTransitionException> { deleted.softDelete("bob", now) }
    }

    @Test
    fun `enabling an ENABLED rule is an illegal transition`() {
        // 严格状态机：重复启用不是幂等静默，而是非法迁移
        assertFailsWith<IllegalTransitionException> { rule().enable("bob", now) }
    }

    @Test
    fun `disabling a DISABLED rule is an illegal transition`() {
        // 严格状态机：重复停用同样是非法迁移
        assertFailsWith<IllegalTransitionException> { rule(status = RuleStatus.DISABLED).disable("bob", now) }
    }

    // ---------- 版本指针 ----------

    @Test
    fun `bumpCurrentVersion then activateVersion publishes the new version`() {
        // 创建草稿版本（current 1->2）后，把新版本设为生效
        val bumped = rule().bumpCurrentVersion("alice", now)
        assertEquals(2, bumped.currentVersion)
        val published = bumped.activateVersion(2, "alice", now)
        assertEquals(2, published.activeVersion)
    }

    @Test
    fun `activateVersion rejects out-of-range version`() {
        // 生效版本号必须在 1..currentVersion 内，越界直接失败
        assertFailsWith<IllegalArgumentException> { rule().activateVersion(9, "bob", now) }
        assertFailsWith<IllegalArgumentException> { rule().activateVersion(0, "bob", now) }
    }

    // ---------- 不变式失败路径 ----------
    // 注意：Kotlin data class 的 copy 不经过 init 块，不变式必须用直接构造验证

    private fun invalidRule(
        ruleKey: String = "anti_fraud_high_amount",
        ruleName: String = "大额订单风控",
        createdBy: String = "alice",
        currentVersion: Int = 1,
        activeVersion: Int? = 1,
    ): Rule =
        Rule(
            ruleKey = ruleKey,
            ruleName = ruleName,
            currentVersion = currentVersion,
            activeVersion = activeVersion,
            createdBy = createdBy,
            createdAt = now,
        )

    @Test
    fun `blank ruleKey or ruleName or createdBy is rejected`() {
        // 标识与元信息非空不变式（直接构造非法实例验证）
        assertFailsWith<IllegalArgumentException> { invalidRule(ruleKey = " ") }
        assertFailsWith<IllegalArgumentException> { invalidRule(ruleName = "") }
        assertFailsWith<IllegalArgumentException> { invalidRule(createdBy = " ") }
    }

    @Test
    fun `non-positive currentVersion is rejected`() {
        // 版本号从 1 起单调递增
        assertFailsWith<IllegalArgumentException> { invalidRule(currentVersion = 0) }
    }

    @Test
    fun `activeVersion beyond currentVersion is rejected`() {
        // 生效版本不可能超前于最新内容版本
        assertFailsWith<IllegalArgumentException> { invalidRule(currentVersion = 1, activeVersion = 2) }
    }

    @Test
    fun `rename rejects blank name`() {
        // 更新名称时的非空校验与构造时一致
        assertFailsWith<IllegalArgumentException> { rule().rename(ruleName = " ", operator = "bob", at = now) }
    }

    // ---------- 值语义 ----------

    @Test
    fun `rules with same fields are equal and transitions return new instances`() {
        // 不可变优先：状态操作返回新实例，原实例保持原状；同字段实例等价（data class 语义）
        val source = rule()
        val disabled = source.disable("bob", now)

        assertEquals(RuleStatus.ENABLED, source.status)
        assertEquals(RuleStatus.DISABLED, disabled.status)
        assertEquals(disabled, rule().disable("bob", now))
    }
}
