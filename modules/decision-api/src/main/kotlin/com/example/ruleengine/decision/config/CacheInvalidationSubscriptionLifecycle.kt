package com.example.ruleengine.decision.config

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * 缓存失效订阅的带重试启动器（阶段 5 优雅降级）。
 *
 * 为什么不直接用容器的 autoStartup：Spring Data Redis 4 的 RedisMessageListenerContainer
 * 在初始订阅失败（Redis 未就绪/不可达）时会从 [RedisMessageListenerContainer.start] 同步抛出，
 * 经 SmartLifecycle 回调导致应用上下文启动失败（FailFast）——违反"Redis 不可用时服务照常启动"
 * 的硬性要求。
 *
 * 本组件接管启动时机：后台守护线程按固定间隔重试 [RedisMessageListenerContainer.start]，
 * 成功即退出（订阅建立后的断线重连由容器自身的恢复退避 + Lettuce 自理）；
 * 应用关闭时停止重试并停掉容器（容器未启动时 stop 为安全空操作）。
 */
class CacheInvalidationSubscriptionLifecycle(
    private val container: RedisMessageListenerContainer,
    private val retryIntervalMs: Long = DEFAULT_RETRY_INTERVAL_MS,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(CacheInvalidationSubscriptionLifecycle::class.java)

    @Volatile
    private var running = false

    @Volatile
    private var worker: Thread? = null

    override fun start() {
        if (running) {
            return
        }
        running = true
        worker =
            Thread(
                {
                    while (running) {
                        try {
                            container.start()
                            log.info("缓存失效广播订阅已建立: topic={}", "ruleengine:cache:invalidate")
                            return@Thread
                        } catch (e: Exception) {
                            log.warn("Redis 缓存失效订阅建立失败, {}ms 后重试: {}", retryIntervalMs, e.toString())
                        }
                        try {
                            Thread.sleep(retryIntervalMs)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@Thread
                        }
                    }
                },
                "cache-invalidation-subscription-starter",
            ).apply {
                isDaemon = true
                start()
            }
    }

    override fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        container.stop()
    }

    override fun isRunning(): Boolean = running

    companion object {
        /** 订阅建立重试间隔（Redis 恢复感知延迟上限；建立后的断线由容器/Lettuce 自理） */
        const val DEFAULT_RETRY_INTERVAL_MS: Long = 5_000L
    }
}
