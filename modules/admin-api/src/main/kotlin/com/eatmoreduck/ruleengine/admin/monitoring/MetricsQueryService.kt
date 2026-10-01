package com.eatmoreduck.ruleengine.admin.monitoring

import com.eatmoreduck.ruleengine.admin.analytics.nearestRankPercentile
import com.eatmoreduck.ruleengine.admin.dto.ExecutionStatsResponse
import com.eatmoreduck.ruleengine.domain.RuleStatus
import com.eatmoreduck.ruleengine.storage.log.ExecutionLogRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleRepository
import com.eatmoreduck.ruleengine.storage.repository.RuleSearchQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 规则执行监控指标查询服务（对应旧 MetricsController 背后的 Micrometer 指标面）。
 *
 * 数据源迁移说明：旧实现汇总 admin 进程内的 rule.execution.* 计数器/计时器
 * （重启清零、多实例不可聚合、跨服务不可见）；新架构决策节点完全无状态，
 * 唯一事实源是 execution_logs 表，本服务以 SQL 聚合得出同口径指标：
 * - 命中判定：output_decision = 'PASS'（旧 recordExecution 的 hit 判定）
 * - 错误计数：status = 'ERROR'（旧 recordError 计数器）
 * - 平均耗时：execution_time_ms 均值（旧 Timer.mean）
 * 百分比与耗时的四舍五入口径（保留两位小数）与旧实现一致。
 */
@Service
class MetricsQueryService(
    private val executionLogRepository: ExecutionLogRepository,
    private val ruleRepository: RuleRepository,
) {
    /** 监控总览：总执行次数、命中次数、命中率、平均耗时、错误次数、错误率 */
    @Transactional(readOnly = true)
    fun getOverview(): Map<String, Any> {
        val summary = executionLogRepository.aggregateTotal()
        val hitRate =
            if (summary.totalExecutions > 0) {
                summary.hitCount.toDouble() / summary.totalExecutions * 100
            } else {
                0.0
            }
        val errorRate =
            if (summary.totalExecutions > 0) {
                summary.errorCount.toDouble() / summary.totalExecutions * 100
            } else {
                0.0
            }
        return linkedMapOf(
            "totalExecutions" to summary.totalExecutions,
            "hitCount" to summary.hitCount,
            "hitRate" to round2(hitRate),
            "avgExecutionTime" to round2(summary.avgExecutionTimeMs),
            "errorCount" to summary.errorCount,
            "errorRate" to round2(errorRate),
        )
    }

    /**
     * 规则执行排行（按指标排序 + 截断）。
     *
     * 与旧实现的两处确定性行为一致：默认按 executionCount、desc 方向排序，
     * limit>0 时截断。排序并列时按 ruleKey 升序保持稳定（旧 HashSet 顺序不确定）。
     */
    @Transactional(readOnly = true)
    fun getRuleRanking(
        sortBy: String,
        sortOrder: String,
        limit: Int,
    ): List<Map<String, Any>> {
        val stats = executionLogRepository.aggregatePerRule()
        val rulesByName =
            ruleRepository
                .search(RuleSearchQuery(includeDeleted = false, limit = MAX_SCAN))
                .associateBy { it.ruleKey }

        val rows =
            stats.map { s ->
                val rule = rulesByName[s.ruleKey]
                RankingRow(
                    ruleKey = s.ruleKey,
                    ruleName = rule?.ruleName ?: s.ruleKey,
                    executionCount = s.executionCount,
                    hitCount = s.hitCount,
                    hitRate =
                        round2(
                            if (s.executionCount > 0) {
                                s.hitCount.toDouble() / s.executionCount * 100
                            } else {
                                0.0
                            },
                        ),
                    avgExecutionTime = round2(s.avgExecutionTimeMs),
                    errorCount = s.errorCount,
                    // 旧实现因指标注册表拿不到规则元数据而恒置 true；
                    // 此处返回 rules 表的真实启用状态（删除/停用规则报 false）
                    enabled = rule?.status == RuleStatus.ENABLED,
                )
            }

        val comparator =
            when (sortBy) {
                "avgExecutionTime" -> compareBy<RankingRow> { it.avgExecutionTime }
                "hitRate" -> compareBy { it.hitRate }
                "errorCount" -> compareBy { it.errorCount }
                else -> compareBy { it.executionCount }
            }.thenBy { it.ruleKey }
        val ordered =
            if (sortOrder.equals("desc", ignoreCase = true)) {
                rows.sortedWith(comparator.reversed())
            } else {
                rows.sortedWith(comparator)
            }
        val limited = if (limit > 0 && ordered.size > limit) ordered.subList(0, limit) else ordered
        return limited.map { row ->
            linkedMapOf(
                "ruleKey" to row.ruleKey,
                "ruleName" to row.ruleName,
                "executionCount" to row.executionCount,
                "hitCount" to row.hitCount,
                "hitRate" to row.hitRate,
                "avgExecutionTime" to row.avgExecutionTime,
                "errorCount" to row.errorCount,
                "enabled" to row.enabled,
            )
        }
    }

    /** 指定规则的执行统计（旧 getExecutionStats；p95 为日志明细的最近邻分位） */
    @Transactional(readOnly = true)
    fun getRuleStats(ruleKey: String): ExecutionStatsResponse {
        val logs = executionLogRepository.findLogsByRuleKey(ruleKey)
        val times = logs.mapNotNull { it.executionTimeMs }.sorted()
        return ExecutionStatsResponse(
            totalExecutions = logs.size.toLong(),
            hitCount = logs.count { it.outputDecision == DECISION_PASS }.toLong(),
            errorCount = logs.count { it.status == STATUS_ERROR }.toLong(),
            avgExecutionTimeMs = if (times.isEmpty()) 0.0 else times.sum().toDouble() / times.size,
            p95ExecutionTimeMs = nearestRankPercentile(times, 0.95),
            lastExecutedAt = logs.maxOfOrNull { it.createdAt },
        )
    }

    /** 排行中间行模型（避免裸 Map 参与排序的类型转换） */
    private data class RankingRow(
        val ruleKey: String,
        val ruleName: String,
        val executionCount: Long,
        val hitCount: Long,
        val hitRate: Double,
        val avgExecutionTime: Double,
        val errorCount: Long,
        val enabled: Boolean,
    )

    companion object {
        private const val DECISION_PASS = "PASS"
        private const val STATUS_ERROR = "ERROR"

        /** 规则元数据装载的最大扫描行数（与 RuleService 同一防护口径） */
        const val MAX_SCAN: Int = 10_000

        /** 保留两位小数（照搬旧 Math.round(x * 100.0) / 100.0） */
        internal fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0
    }
}
