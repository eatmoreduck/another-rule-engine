package com.eatmoreduck.ruleengine.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 灰度发布状态机、时间戳不变式与分流判断测试。
 */
class GrayscaleReleaseTest {
    private val t0: Instant = Instant.parse("2026-04-10T00:00:00Z")
    private val t1: Instant = Instant.parse("2026-04-10T01:00:00Z")
    private val t2: Instant = Instant.parse("2026-04-10T02:00:00Z")

    private fun release(
        status: GrayscaleStatus = GrayscaleStatus.DRAFT,
        startedAt: Instant? = null,
        completedAt: Instant? = null,
        policy: GrayscalePolicy = GrayscalePolicy.Percentage(10),
    ): GrayscaleRelease =
        GrayscaleRelease(
            target = GrayscaleTarget(GrayscaleTargetType.RULE, "anti_fraud_high_amount"),
            currentVersion = 3,
            grayscaleVersion = 4,
            policy = policy,
            status = status,
            createdBy = "alice",
            createdAt = t0,
            startedAt = startedAt,
            completedAt = completedAt,
        )

    // ---------- 状态机：合法迁移 ----------

    @Test
    fun `DRAFT can start to RUNNING`() {
        // 启动灰度：记录 startedAt，进入 RUNNING
        val running = release().start(t1)
        assertEquals(GrayscaleStatus.RUNNING, running.status)
        assertEquals(t1, running.startedAt)
    }

    @Test
    fun `PAUSED can resume to RUNNING`() {
        // 暂停后恢复启动（旧 startGrayscale 允许 DRAFT 或 PAUSED 启动）
        val resumed = release(status = GrayscaleStatus.PAUSED, startedAt = t1).start(t2)
        assertEquals(GrayscaleStatus.RUNNING, resumed.status)
        assertEquals(t2, resumed.startedAt)
    }

    @Test
    fun `RUNNING can pause`() {
        // 运行中可暂停
        val paused = release(status = GrayscaleStatus.RUNNING, startedAt = t1).pause()
        assertEquals(GrayscaleStatus.PAUSED, paused.status)
        assertNotNull(paused.startedAt)
        assertNull(paused.completedAt)
    }

    @Test
    fun `RUNNING can complete`() {
        // 全量切换：记录 completedAt，进入终态 COMPLETED
        val completed = release(status = GrayscaleStatus.RUNNING, startedAt = t1).complete(t2)
        assertEquals(GrayscaleStatus.COMPLETED, completed.status)
        assertEquals(t2, completed.completedAt)
    }

    @Test
    fun `PAUSED can complete`() {
        // 旧 completeGrayscale 允许 RUNNING 或 PAUSED 完成
        val completed = release(status = GrayscaleStatus.PAUSED, startedAt = t1).complete(t2)
        assertEquals(GrayscaleStatus.COMPLETED, completed.status)
    }

    @Test
    fun `DRAFT can roll back directly`() {
        // 未启动即放弃：旧 rollbackGrayscale 只排除 COMPLETED 与 ROLLED_BACK
        val rolledBack = release().rollback(t1)
        assertEquals(GrayscaleStatus.ROLLED_BACK, rolledBack.status)
        assertEquals(t1, rolledBack.completedAt)
    }

    @Test
    fun `RUNNING and PAUSED can roll back`() {
        // 运行中/暂停中回滚均为合法迁移
        val fromRunning = release(status = GrayscaleStatus.RUNNING, startedAt = t1).rollback(t2)
        val fromPaused = release(status = GrayscaleStatus.PAUSED, startedAt = t1).rollback(t2)
        assertEquals(GrayscaleStatus.ROLLED_BACK, fromRunning.status)
        assertEquals(GrayscaleStatus.ROLLED_BACK, fromPaused.status)
    }

    // ---------- 状态机：非法迁移 ----------

    @Test
    fun `DRAFT cannot pause or complete`() {
        // 未启动的灰度不能暂停，也不能跳过运行直接完成
        val draft = release()
        assertFailsWith<IllegalTransitionException> { draft.pause() }
        assertFailsWith<IllegalTransitionException> { draft.complete(t1) }
    }

    @Test
    fun `terminal states reject any transition`() {
        // COMPLETED 与 ROLLED_BACK 为终态（旧守卫：已完成或已回滚的配置不能再次回滚，也不能重启）
        val completed = release(status = GrayscaleStatus.COMPLETED, startedAt = t1, completedAt = t2)
        assertFailsWith<IllegalTransitionException> { completed.start(t2) }
        assertFailsWith<IllegalTransitionException> { completed.pause() }
        assertFailsWith<IllegalTransitionException> { completed.rollback(t2) }

        val rolledBack = release(status = GrayscaleStatus.ROLLED_BACK, startedAt = t1, completedAt = t2)
        assertFailsWith<IllegalTransitionException> { rolledBack.start(t2) }
        assertFailsWith<IllegalTransitionException> { rolledBack.rollback(t2) }
    }

    // ---------- 分流判断 ----------

    @Test
    fun `only RUNNING releases divert traffic to the canary version`() {
        // 仅 RUNNING 参与分流：PAUSED 即使策略命中也不分流（与旧 resolveGrayscaleVersion 语义一致）
        val features = mapOf("userId" to "u1")
        val paused =
            release(
                status = GrayscaleStatus.PAUSED,
                startedAt = t1,
                policy = GrayscalePolicy.Percentage(100),
            )
        assertFalse(paused.isCanaryRequest(features))

        val running = release(policy = GrayscalePolicy.Percentage(100)).start(t1)
        assertTrue(running.isCanaryRequest(features))

        val draft = release(policy = GrayscalePolicy.Percentage(100))
        assertFalse(draft.isCanaryRequest(features))
    }

    @Test
    fun `canary diverting respects the policy verdict`() {
        // RUNNING 状态下由策略判定：白名单命中走灰度
        val running =
            release(
                policy = GrayscalePolicy.Whitelist(setOf("vip-1")),
            ).start(t1)
        assertTrue(running.isCanaryRequest(mapOf("userId" to "vip-1")))
        assertFalse(running.isCanaryRequest(mapOf("userId" to "guest")))
    }

    // ---------- 不变式失败路径（直接构造，copy 不经过 init） ----------

    @Test
    fun `grayscale version must differ from current version`() {
        // 同版本灰度无意义，构造即失败
        assertFailsWith<IllegalArgumentException> {
            GrayscaleRelease(
                target = GrayscaleTarget(GrayscaleTargetType.RULE, "anti_fraud_high_amount"),
                currentVersion = 4,
                grayscaleVersion = 4,
                policy = GrayscalePolicy.Percentage(10),
                createdBy = "alice",
                createdAt = t0,
            )
        }
    }

    @Test
    fun `timestamp fields must be consistent with status`() {
        // 时间戳与状态的一致性不变式
        assertFailsWith<IllegalArgumentException> { release(startedAt = t1) } // DRAFT 不允许 startedAt
        assertFailsWith<IllegalArgumentException> { release(completedAt = t1) } // DRAFT 不允许 completedAt
        assertFailsWith<IllegalArgumentException> {
            release(status = GrayscaleStatus.RUNNING, startedAt = null) // RUNNING 缺 startedAt
        }
        assertFailsWith<IllegalArgumentException> {
            release(
                status = GrayscaleStatus.RUNNING,
                startedAt = t1,
                completedAt = t2, // RUNNING 不允许 completedAt
            )
        }
        assertFailsWith<IllegalArgumentException> {
            release(status = GrayscaleStatus.COMPLETED, startedAt = t1, completedAt = null) // COMPLETED 缺 completedAt
        }
    }

    @Test
    fun `non-positive versions and blank createdBy are rejected`() {
        // 版本号与创建人非空/非负不变式（直接构造非法实例验证）
        fun build(
            currentVersion: Int = 3,
            grayscaleVersion: Int = 4,
            createdBy: String = "alice",
        ): GrayscaleRelease =
            GrayscaleRelease(
                target = GrayscaleTarget(GrayscaleTargetType.RULE, "anti_fraud_high_amount"),
                currentVersion = currentVersion,
                grayscaleVersion = grayscaleVersion,
                policy = GrayscalePolicy.Percentage(10),
                createdBy = createdBy,
                createdAt = t0,
            )

        assertFailsWith<IllegalArgumentException> { build(currentVersion = 0) }
        assertFailsWith<IllegalArgumentException> { build(grayscaleVersion = 0) }
        assertFailsWith<IllegalArgumentException> { build(createdBy = " ") }
    }

    // ---------- 值语义 ----------

    @Test
    fun `releases with same fields are equal`() {
        // data class 等价性
        assertEquals(release(), release())
        assertEquals(
            GrayscaleTarget(GrayscaleTargetType.RULE, "k"),
            GrayscaleTarget(GrayscaleTargetType.RULE, "k"),
        )
    }

    @Test
    fun `blank target key is rejected`() {
        // 灰度目标 key 非空白
        assertFailsWith<IllegalArgumentException> { GrayscaleTarget(GrayscaleTargetType.RULE, " ") }
    }
}
