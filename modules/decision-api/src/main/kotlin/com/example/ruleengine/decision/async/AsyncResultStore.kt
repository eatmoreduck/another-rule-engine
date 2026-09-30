package com.example.ruleengine.decision.async

import com.example.ruleengine.decision.config.ApplicationCoroutineScope
import com.example.ruleengine.decision.config.DecisionProperties
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 异步决策结果存储（语义移植旧 AsyncResultStore）：
 * - ConcurrentHashMap 存储「提交 → 轮询」的中间结果，决策节点无状态约束下的进程内暂存
 *   （实例粘性轮询由调用方保证；跨实例共享属阶段 5 Redis 会话范畴）；
 * - 过期读取：超期条目读取即清理并视为不存在；
 * - 定时清理：协程按 [DecisionProperties.asyncCleanupIntervalSeconds] 周期清扫，防内存泄漏。
 */
@Component
class AsyncResultStore(
    properties: DecisionProperties,
    scope: ApplicationCoroutineScope,
) {
    private val expireSeconds = properties.asyncResultExpireSeconds

    private val store = ConcurrentHashMap<String, Entry>()

    /** 当前存活条目数（监控用） */
    fun size(): Int = store.size

    /** 过期清理累计移除数（测试与监控用） */
    val totalExpired: AtomicLong = AtomicLong()

    fun storeResult(
        requestId: String,
        outcome: AsyncDecisionOutcome,
    ) {
        if (requestId.isEmpty()) return
        store[requestId] = Entry(outcome, System.currentTimeMillis())
    }

    fun getResult(requestId: String): AsyncDecisionOutcome? {
        val entry = store[requestId] ?: return null
        if (entry.isExpired(expireSeconds)) {
            store.remove(requestId)
            return null
        }
        return entry.outcome
    }

    fun removeResult(requestId: String) {
        store.remove(requestId)
    }

    init {
        scope.launch(CoroutineName("async-result-cleanup")) {
            while (isActive) {
                delay(properties.asyncCleanupIntervalSeconds * 1000)
                cleanupExpired()
            }
        }
    }

    /** 清扫过期条目，返回移除数 */
    fun cleanupExpired(): Int {
        var removed = 0
        for ((key, entry) in store) {
            if (entry.isExpired(expireSeconds)) {
                if (store.remove(key, entry)) removed++
            }
        }
        if (removed > 0) totalExpired.addAndGet(removed.toLong())
        return removed
    }

    private class Entry(
        val outcome: AsyncDecisionOutcome,
        val createdAt: Long,
    ) {
        fun isExpired(expireSeconds: Long): Boolean = System.currentTimeMillis() - createdAt > expireSeconds * 1000
    }
}
