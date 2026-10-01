package com.eatmoreduck.ruleengine.admin.analytics

import com.eatmoreduck.ruleengine.admin.dto.RuleAnalyticsResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleAnalyticsResponse.TrendDataPoint
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogEntry
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 规则效果分析服务（对应旧 RuleAnalyticsService：MON-03 命中率/拦截率/误判口径）。
 *
 * 聚合口径逐字段照搬旧实现：
 * - hitCount：status = 'SUCCESS'（旧实现的口径即如此，非决策命中）
 * - rejectCount / passCount：output_decision 精确等于 REJECT / PASS
 * - errorCount：status ∈ {ERROR, TIMEOUT}
 * - 比率为 0-100 百分比，不做四舍五入（与 metrics 接口不同）
 * - 时间窗为闭区间 [startDate 00:00, endDate 23:59:59.999...]，时区取 JVM 默认
 *   （与旧 LocalDateTime + JPA Between 的语义一致）
 */
@Service
class RuleAnalyticsService(
    private val executionLogRepository: ExecutionLogRepository,
    private val ruleRepository: RuleRepository,
) {
    /** 单规则效果分析 */
    @Transactional(readOnly = true)
    fun getAnalytics(
        ruleKey: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): RuleAnalyticsResponse {
        val (start, end) = toInclusiveRange(startDate, endDate)
        val logs = executionLogRepository.findLogsByRuleKeyAndTimeRange(ruleKey, start, end)
        val ruleName = ruleRepository.findByRuleKey(ruleKey)?.ruleName ?: ruleKey
        return buildAnalytics(ruleKey, ruleName, logs, startDate, endDate)
    }

    /** 全局分析概览（时间窗内出现日志的规则各一条；无日志的规则不出现在结果中，与旧一致） */
    @Transactional(readOnly = true)
    fun getOverview(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<RuleAnalyticsResponse> {
        val (start, end) = toInclusiveRange(startDate, endDate)
        return executionLogRepository
            .findLogsByTimeRange(start, end)
            .groupBy { it.ruleKey }
            .map { (ruleKey, logs) ->
                val ruleName = ruleRepository.findByRuleKey(ruleKey)?.ruleName ?: ruleKey
                buildAnalytics(ruleKey, ruleName, logs, startDate, endDate)
            }
    }

    // ---------- 私有辅助 ----------

    /** 构建规则分析数据（计数/比率/耗时统计/按天趋势，算法照搬旧 buildAnalytics） */
    private fun buildAnalytics(
        ruleKey: String,
        ruleName: String,
        logs: List<ExecutionLogEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): RuleAnalyticsResponse {
        val totalExecutions = logs.size.toLong()
        val hitCount = logs.count { it.status == STATUS_SUCCESS }.toLong()
        val rejectCount = logs.count { it.outputDecision == DECISION_REJECT }.toLong()
        val passCount = logs.count { it.outputDecision == DECISION_PASS }.toLong()
        val errorCount = logs.count { it.status == STATUS_ERROR || it.status == STATUS_TIMEOUT }.toLong()

        val executionTimes = logs.mapNotNull { it.executionTimeMs }.sorted()

        return RuleAnalyticsResponse(
            ruleKey = ruleKey,
            ruleName = ruleName,
            totalExecutions = totalExecutions,
            hitCount = hitCount,
            hitRate = rate(hitCount, totalExecutions),
            rejectCount = rejectCount,
            rejectRate = rate(rejectCount, totalExecutions),
            passCount = passCount,
            passRate = rate(passCount, totalExecutions),
            errorCount = errorCount,
            errorRate = rate(errorCount, totalExecutions),
            avgExecutionTimeMs = if (executionTimes.isEmpty()) 0.0 else executionTimes.sum().toDouble() / executionTimes.size,
            maxExecutionTimeMs = executionTimes.lastOrNull()?.toDouble() ?: 0.0,
            p99ExecutionTimeMs = nearestRankPercentile(executionTimes, 0.99),
            trendData = buildTrendData(logs, startDate, endDate),
        )
    }

    /** 按天趋势数据（窗口内逐日补零；date 为 ISO-8601 字符串，照搬旧 buildTrendData） */
    private fun buildTrendData(
        logs: List<ExecutionLogEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<TrendDataPoint> {
        val zone = ZoneId.systemDefault()
        val byDate = logs.groupBy { it.createdAt.atZone(zone).toLocalDate() }

        val trendData = mutableListOf<TrendDataPoint>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            val dayLogs = byDate[date].orEmpty()
            val dayTotal = dayLogs.size.toLong()
            val dayHits = dayLogs.count { it.status == STATUS_SUCCESS }.toLong()
            val dayTimes = dayLogs.mapNotNull { it.executionTimeMs }
            trendData +=
                TrendDataPoint(
                    date = date.toString(),
                    executions = dayTotal,
                    hits = dayHits,
                    hitRate = rate(dayHits, dayTotal),
                    avgExecutionTimeMs = if (dayTimes.isEmpty()) 0.0 else dayTimes.sum().toDouble() / dayTimes.size,
                )
            date = date.plusDays(1)
        }
        return trendData
    }

    /** 0-100 百分比（分母为零时为 0；不四舍五入，与旧实现一致） */
    private fun rate(
        count: Long,
        total: Long,
    ): Double = if (total > 0) count.toDouble() / total * 100 else 0.0

    /** 日期窗口 → 闭区间 Instant 对（startDate 0 点 ~ endDate 当日最后一纳秒，JVM 默认时区） */
    private fun toInclusiveRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        return startDate.atStartOfDay(zone).toInstant() to endDate.atTime(LocalTime.MAX).atZone(zone).toInstant()
    }

    companion object {
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_TIMEOUT = "TIMEOUT"
        private const val STATUS_ERROR = "ERROR"
        private const val DECISION_PASS = "PASS"
        private const val DECISION_REJECT = "REJECT"
    }
}

/**
 * 最近邻分位值（照搬旧 getP99 的取整算法）：
 * index = ceil(n * quantile) - 1，并夹在 [0, n-1]；空列表返回 0。
 */
internal fun nearestRankPercentile(
    sortedTimes: List<Int>,
    quantile: Double,
): Double {
    if (sortedTimes.isEmpty()) return 0.0
    val index = ceil(sortedTimes.size * quantile).toInt() - 1
    return sortedTimes[index.coerceIn(0, sortedTimes.size - 1)].toDouble()
}
