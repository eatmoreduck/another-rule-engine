package com.eatmoreduck.ruleengine.decision.log

import com.eatmoreduck.ruleengine.decision.config.ApplicationCoroutineScope
import com.eatmoreduck.ruleengine.decision.config.DecisionProperties
import com.eatmoreduck.ruleengine.storage.log.CanaryExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.log.CanaryExecutionLogRow
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogRow
import io.micrometer.core.instrument.MeterRegistry
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Instant

/**
 * 执行日志服务：决策链路的日志写入面（全部异步缓冲批量落库，绝不阻塞决策链路）。
 *
 * - execution_logs：每次决策的输入/输出/耗时/状态（对应旧 ExecutionLogService 的 logSuccess/logTimeout/logError，
 *   旧实现的 @Async 单条即时写改为内存队列批量刷盘，决策热路径零 DB 开销）；
 * - canary_execution_log：灰度分流的每次执行详情（对应旧 CanaryExecutionLogService.asyncLog/asyncLogError）。
 *
 * 版本号口径（与旧实现的差异点）：旧 RuleExecutionService 记日志时 ruleVersion 恒传 null，
 * 本实现写入钉住的版本号（决策内已可获得，信息更完整；表列语义本就是"规则版本号"）。
 */
@Service
class ExecutionLogService(
    executionLogRepository: ExecutionLogRepository,
    canaryLogRepository: CanaryExecutionLogRepository,
    scope: ApplicationCoroutineScope,
    registry: MeterRegistry,
    properties: DecisionProperties,
) {
    private val log = LoggerFactory.getLogger(ExecutionLogService::class.java)
    private val mapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    /** execution_logs 批量缓冲 */
    private val executionLogBuffer =
        ExecutionLogBuffer(
            name = "execution-log",
            capacity = properties.logBufferCapacity,
            batchSize = properties.logBatchSize,
            flushIntervalMs = properties.logFlushIntervalMs,
            flush = { batch ->
                transaction { executionLogRepository.insertBatch(batch) }
            },
            scope = scope,
            registry = registry,
        )

    /** canary_execution_log 批量缓冲 */
    private val canaryLogBuffer =
        ExecutionLogBuffer(
            name = "canary-log",
            capacity = properties.logBufferCapacity,
            batchSize = properties.logBatchSize,
            flushIntervalMs = properties.logFlushIntervalMs,
            flush = { batch ->
                transaction { canaryLogRepository.insertBatch(batch) }
            },
            scope = scope,
            registry = registry,
        )

    /** 记录执行成功日志（status=SUCCESS） */
    fun logSuccess(
        ruleKey: String,
        ruleVersion: Int?,
        inputFeatures: Map<String, Any?>?,
        outputDecision: String,
        outputReason: String?,
        executionTimeMs: Long,
    ) {
        submit(
            ExecutionLogRow(
                ruleKey = ruleKey,
                ruleVersion = ruleVersion,
                inputFeatures = featuresJson(inputFeatures),
                outputDecision = outputDecision,
                outputReason = outputReason,
                executionTimeMs = executionTimeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                status = "SUCCESS",
                errorMessage = null,
                createdAt = Instant.now(),
            ),
        )
    }

    /** 记录执行超时日志（status=TIMEOUT，决策恒 REJECT fail-safe） */
    fun logTimeout(
        ruleKey: String,
        ruleVersion: Int?,
        inputFeatures: Map<String, Any?>?,
        executionTimeMs: Long,
    ) {
        submit(
            ExecutionLogRow(
                ruleKey = ruleKey,
                ruleVersion = ruleVersion,
                inputFeatures = featuresJson(inputFeatures),
                outputDecision = "REJECT",
                outputReason = "规则执行超时",
                executionTimeMs = executionTimeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                status = "TIMEOUT",
                errorMessage = null,
                createdAt = Instant.now(),
            ),
        )
    }

    /** 记录执行错误日志（status=ERROR） */
    fun logError(
        ruleKey: String,
        ruleVersion: Int?,
        inputFeatures: Map<String, Any?>?,
        executionTimeMs: Long,
        errorMessage: String?,
    ) {
        submit(
            ExecutionLogRow(
                ruleKey = ruleKey,
                ruleVersion = ruleVersion,
                inputFeatures = featuresJson(inputFeatures),
                outputDecision = "REJECT",
                outputReason = "规则执行失败: $errorMessage",
                executionTimeMs = executionTimeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                status = "ERROR",
                errorMessage = errorMessage,
                createdAt = Instant.now(),
            ),
        )
    }

    /** 异步记录灰度执行日志（仅存在运行中灰度配置的决策写入） */
    fun logCanary(
        traceId: String,
        targetType: String,
        targetKey: String,
        versionUsed: Int,
        isCanary: Boolean,
        requestFeatures: Map<String, Any?>?,
        decisionResult: String,
        executionTimeMs: Long,
    ) {
        canaryLogBuffer.submit(
            CanaryExecutionLogRow(
                traceId = traceId,
                targetType = targetType,
                targetKey = targetKey,
                versionUsed = versionUsed,
                isCanary = isCanary,
                requestFeatures = featuresJson(requestFeatures),
                decisionResult = decisionResult,
                executionTimeMs = executionTimeMs,
                errorMessage = null,
                createdAt = Instant.now(),
            ),
        )
    }

    /** 异步记录灰度执行错误日志 */
    fun logCanaryError(
        traceId: String,
        targetType: String,
        targetKey: String,
        versionUsed: Int,
        isCanary: Boolean,
        requestFeatures: Map<String, Any?>?,
        errorMessage: String,
        executionTimeMs: Long,
    ) {
        canaryLogBuffer.submit(
            CanaryExecutionLogRow(
                traceId = traceId,
                targetType = targetType,
                targetKey = targetKey,
                versionUsed = versionUsed,
                isCanary = isCanary,
                requestFeatures = featuresJson(requestFeatures),
                decisionResult = null,
                executionTimeMs = executionTimeMs,
                errorMessage = errorMessage,
                createdAt = Instant.now(),
            ),
        )
    }

    private fun submit(row: ExecutionLogRow) {
        try {
            executionLogBuffer.submit(row)
        } catch (e: Exception) {
            log.error("执行日志入队失败: ruleKey={}", row.ruleKey, e)
        }
    }

    /** 特征 Map → JSON 文本（序列化失败置 NULL，不阻断日志链路） */
    private fun featuresJson(features: Map<String, Any?>?): String? {
        if (features.isNullOrEmpty()) return null
        return try {
            mapper.writeValueAsString(features)
        } catch (e: JacksonException) {
            log.warn("特征序列化失败, 日志记录为空特征: {}", e.message)
            null
        }
    }
}
