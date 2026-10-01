package com.eatmoreduck.ruleengine.shared.satoken

import cn.dev33.satoken.dao.SaTokenDao
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl
import cn.dev33.satoken.session.SaSession
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Sa-Token 会话存储的 Redis 断级降级包装器（阶段 5 优雅降级硬性要求）。
 *
 * 存储 strategy：
 * - 主存储为 [primary]（部署物内的官方 SaTokenDaoForRedisTemplate，Boot 自动装配），
 *   会话落 Redis，跨服务（admin-api / decision-api）共享 token；
 * - Redis 不可用（[DataAccessException]，连接拒绝/超时/分区）→ 单次操作回退
 *   [SaTokenDaoDefaultImpl] 内存实现：登录、鉴权、登出照常工作，
 *   仅失去跨服务共享（token 只在本实例有效）；
 * - 熔断保护（决策链路 50ms 红线）：连续失败 ≥ [failureThreshold] 次后熔断 [reopenMillis]，
 *   期间所有操作直走内存、不再触碰 Redis（避免网络分区时每次会话操作吃满连接超时）；
 *   冷却结束后半开——下一个操作重试主存储，成功即恢复。
 *
 * 已知取舍（可接受）：
 * - 降级窗口内经内存写入的会话在 Redis 恢复后不存在于 Redis，旧 token 跨服务不可见，
 *   用户重新登录即可（与"token 不跨服务但功能正常"的降级语义一致）；
 * - 降级窗口内"Redis 写入成功后立即读"不存在不一致（熔断打开期间主存不再被触碰）；
 *   半开瞬间的少量读写竞态由 Sa-Token 的会话超时与登录幂等兜底。
 *
 * 序列化说明：对象/会话载荷的 JSON 编解码由主存储自身完成（官方实现经 SaManager 的
 * Jackson 3 模板，带 @class 多态标记）；本类不做任何序列化，只做存储选路与降级。
 *
 * shared 模块约定：本类仅依赖 sa-token-core 与 spring-data-redis 的异常类型（均 compileOnly），
 * 运行时依赖由部署物提供。
 */
class SaTokenOutageFallbackDao(
    private val primary: SaTokenDao,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val reopenMillis: Long = DEFAULT_REOPEN_MILLIS,
) : SaTokenDao {
    /** Redis 不可用时的内存兜底（sa-token-core 自带的默认实现） */
    private val memory: SaTokenDao = SaTokenDaoDefaultImpl()

    private val log = LoggerFactory.getLogger(SaTokenOutageFallbackDao::class.java)

    /** 连续失败计数（熔断窗口内） */
    private val consecutiveFailures = AtomicInteger(0)

    /** 熔断截止时间（epoch millis），0 表示未熔断 */
    private val openUntilMillis = AtomicLong(0)

    // ---------- 选路与熔断 ----------

    /** 熔断打开期间直走内存；冷却结束后半开放行主存储 */
    private fun primaryAllowed(): Boolean = System.currentTimeMillis() >= openUntilMillis.get()

    /** 单次操作统一选路：主存储优先，DataAccessException 即降级 */
    private fun <T> viaRedis(
        fallback: () -> T,
        primaryOp: () -> T,
    ): T {
        if (!primaryAllowed()) {
            return fallback()
        }
        return try {
            val result = primaryOp()
            onPrimarySuccess()
            result
        } catch (e: DataAccessException) {
            onPrimaryFailure(e)
            fallback()
        }
    }

    private fun onPrimaryFailure(e: DataAccessException) {
        val count = consecutiveFailures.incrementAndGet()
        if (count == 1) {
            log.warn("Redis 会话存储访问失败, 本次降级为内存会话: {}", e.toString())
        }
        if (count >= failureThreshold) {
            openUntilMillis.set(System.currentTimeMillis() + reopenMillis)
            consecutiveFailures.set(0)
            log.warn(
                "Redis 连续失败 {} 次, 熔断 {}ms 内会话操作直走内存（冷却后半开重试）: {}",
                failureThreshold,
                reopenMillis,
                e.toString(),
            )
        }
    }

    private fun onPrimarySuccess() {
        if (openUntilMillis.get() > 0 || consecutiveFailures.get() > 0) {
            log.info("Redis 会话存储恢复, 切回主存储")
        }
        consecutiveFailures.set(0)
        openUntilMillis.set(0)
    }

    // ---------- 字符串层（token 映射、临时令牌等） ----------

    override fun get(key: String): String? = viaRedis({ memory.get(key) }, { primary.get(key) })

    override fun set(
        key: String,
        value: String,
        timeout: Long,
    ) {
        viaRedis({ memory.set(key, value, timeout) }, { primary.set(key, value, timeout) })
    }

    override fun update(
        key: String,
        value: String,
    ) {
        viaRedis({ memory.update(key, value) }, { primary.update(key, value) })
    }

    override fun delete(key: String) {
        viaRedis({ memory.delete(key) }, { primary.delete(key) })
    }

    override fun getTimeout(key: String): Long = viaRedis({ memory.getTimeout(key) }, { primary.getTimeout(key) })

    override fun updateTimeout(
        key: String,
        timeout: Long,
    ) {
        viaRedis({ memory.updateTimeout(key, timeout) }, { primary.updateTimeout(key, timeout) })
    }

    // ---------- 对象层（Token-Session 等任意对象） ----------

    override fun getObject(key: String): Any? = viaRedis({ memory.getObject(key) }, { primary.getObject(key) })

    override fun <T> getObject(
        key: String,
        type: Class<T>,
    ): T = viaRedis({ memory.getObject(key, type) }, { primary.getObject(key, type) })

    override fun setObject(
        key: String,
        value: Any,
        timeout: Long,
    ) {
        viaRedis({ memory.setObject(key, value, timeout) }, { primary.setObject(key, value, timeout) })
    }

    override fun updateObject(
        key: String,
        value: Any,
    ) {
        viaRedis({ memory.updateObject(key, value) }, { primary.updateObject(key, value) })
    }

    override fun deleteObject(key: String) {
        viaRedis({ memory.deleteObject(key) }, { primary.deleteObject(key) })
    }

    override fun getObjectTimeout(key: String): Long = viaRedis({ memory.getObjectTimeout(key) }, { primary.getObjectTimeout(key) })

    override fun updateObjectTimeout(
        key: String,
        timeout: Long,
    ) {
        viaRedis({ memory.updateObjectTimeout(key, timeout) }, { primary.updateObjectTimeout(key, timeout) })
    }

    // ---------- 会话层（Account-Session） ----------

    override fun getSession(key: String): SaSession? = viaRedis({ memory.getSession(key) }, { primary.getSession(key) })

    override fun setSession(
        session: SaSession,
        timeout: Long,
    ) {
        viaRedis({ memory.setSession(session, timeout) }, { primary.setSession(session, timeout) })
    }

    override fun updateSession(session: SaSession) {
        viaRedis({ memory.updateSession(session) }, { primary.updateSession(session) })
    }

    override fun deleteSession(key: String) {
        viaRedis({ memory.deleteSession(key) }, { primary.deleteSession(key) })
    }

    override fun getSessionTimeout(key: String): Long = viaRedis({ memory.getSessionTimeout(key) }, { primary.getSessionTimeout(key) })

    override fun updateSessionTimeout(
        key: String,
        timeout: Long,
    ) {
        viaRedis({ memory.updateSessionTimeout(key, timeout) }, { primary.updateSessionTimeout(key, timeout) })
    }

    // ---------- 检索（管理端会话搜索；本产品未暴露对应端点，实现以备完整契约） ----------

    override fun searchData(
        prefix: String,
        keyword: String,
        start: Int,
        size: Int,
        sortType: Boolean,
    ): List<String> =
        viaRedis({
            memory.searchData(prefix, keyword, start, size, sortType)
        }, { primary.searchData(prefix, keyword, start, size, sortType) })

    companion object {
        /** 默认熔断阈值：连续失败 3 次（约 3 个请求内完成熔断） */
        const val DEFAULT_FAILURE_THRESHOLD: Int = 3

        /** 默认熔断时长：30s 冷却后半开重试（Redis 恢复感知延迟上限） */
        const val DEFAULT_REOPEN_MILLIS: Long = 30_000L
    }
}
