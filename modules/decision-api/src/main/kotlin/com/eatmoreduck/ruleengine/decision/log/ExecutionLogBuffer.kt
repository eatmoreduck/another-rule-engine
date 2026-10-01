package com.eatmoreduck.ruleengine.decision.log

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * 通用异步批量刷盘缓冲：决策热路径 [submit] 永不阻塞（trySend 入队，队列满丢最旧并计数），
 * 后台协程按「攒满 [batchSize] 行」或「等待 [flushIntervalMs]」先到者刷盘。
 *
 * 刷盘失败：整批丢弃并告警（执行日志为可丢数据，重试可能反复失败拖垮后台协程），
 * 丢弃量经 decision.log.dropped 指标与内部计数暴露。
 *
 * @param name 缓冲名（协程与指标命名用）
 */
class ExecutionLogBuffer<T>(
    name: String,
    capacity: Int,
    private val batchSize: Int,
    private val flushIntervalMs: Long,
    private val flush: suspend (List<T>) -> Unit,
    scope: CoroutineScope,
    registry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(ExecutionLogBuffer::class.java)

    /** 丢弃计数器（队列满 + 刷盘失败，按缓冲名分标签） */
    private val dropCounter: Counter =
        Counter
            .builder("decision.log.dropped")
            .tag("buffer", name)
            .register(registry)

    /** 队列满丢弃的行数（进程内存活累计，供测试断言） */
    val droppedByOverflow: AtomicLong = AtomicLong()

    /** 刷盘失败丢弃的行数（供测试断言） */
    val droppedByFlushFailure: AtomicLong = AtomicLong()

    private val channel = Channel<T>(capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch(CoroutineName("execution-log-$name")) { consumeLoop() }
    }

    /** 提交一条日志（非阻塞；队列满丢弃并计数，绝不影响决策链路） */
    fun submit(item: T) {
        if (!channel.trySend(item).isSuccess) {
            droppedByOverflow.incrementAndGet()
            dropCounter.increment()
        }
    }

    private suspend fun consumeLoop() {
        val batch = ArrayList<T>(batchSize)
        while (coroutineContext.isActive) {
            // 阻塞等待首条（带超时以便停机感知与空转退出）
            val head = withTimeoutOrNull(flushIntervalMs * 10) { channel.receive() } ?: continue
            batch.add(head)
            // 攒批窗口：batchSize 或 flushIntervalMs 先到者刷盘
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushIntervalMs)
            while (batch.size < batchSize) {
                val remainingNs = deadline - System.nanoTime()
                if (remainingNs <= 0) break
                val next = withTimeoutOrNull(TimeUnit.NANOSECONDS.toMillis(remainingNs)) { channel.receive() } ?: break
                batch.add(next)
            }
            flushBatch(batch)
            batch.clear()
        }
    }

    private suspend fun flushBatch(batch: List<T>) {
        if (batch.isEmpty()) return
        try {
            flush(batch)
        } catch (e: Exception) {
            droppedByFlushFailure.addAndGet(batch.size.toLong())
            dropCounter.increment(batch.size.toDouble())
            log.error("执行日志刷盘失败, 丢弃 {} 行: buffer={}, cause={}", batch.size, e.javaClass.simpleName, e.message)
        }
    }
}
