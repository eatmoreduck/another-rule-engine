package com.eatmoreduck.ruleengine.decision.repo

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.div
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

/**
 * grayscale_metrics 最小表对象（列与 V6 基线一致；零值行由 admin-api 创建灰度时初始化）。
 * decision-api 只做执行计数递增，不读不初始化。
 */
internal object GrayscaleMetricsTable : Table("grayscale_metrics") {
    val id = long("id")
    val configId = long("grayscale_config_id")
    val version = integer("version")
    val executionCount = integer("execution_count")
    val hitCount = integer("hit_count")
    val errorCount = integer("error_count")
    val avgExecutionTimeMs = integer("avg_execution_time_ms")

    override val primaryKey = PrimaryKey(id)
}

/**
 * 灰度执行指标递增（对应旧 GrayscaleMetricRepository.incrementMetrics 的原子 UPDATE）。
 * 接口化以便决策链路单测替换桩实现。
 */
interface GrayscaleMetricsCounter {
    /**
     * @return 更新行数；0 表示指标行不存在（admin-api 未初始化或已被清理，仅告警不阻断）
     */
    fun increment(
        configId: Long,
        version: Int,
        execTimeMs: Int,
        isSuccess: Boolean,
    ): Int
}

/**
 * [GrayscaleMetricsCounter] 的 Exposed 实现：
 * SQL 端原子递增（无 read-modify-write），并发决策下不丢计数；
 * 平均耗时沿用旧 JPQL 的整型除法口径：
 * `avg' = (avg * count + execTime) / (count + 1)`（PG 整数除法向零截断）。
 * 命中/错误计数在编译期按 [isSuccess] 决定 +1 或 +0（与旧 CASE WHEN 等价）。
 */
@Repository
class ExposedGrayscaleMetricsCounter : GrayscaleMetricsCounter {
    override fun increment(
        configId: Long,
        version: Int,
        execTimeMs: Int,
        isSuccess: Boolean,
    ): Int =
        transaction {
            GrayscaleMetricsTable.update(
                { (GrayscaleMetricsTable.configId eq configId) and (GrayscaleMetricsTable.version eq version) },
            ) { statement ->
                statement[GrayscaleMetricsTable.executionCount] = GrayscaleMetricsTable.executionCount + 1
                statement[GrayscaleMetricsTable.avgExecutionTimeMs] =
                    (GrayscaleMetricsTable.avgExecutionTimeMs * GrayscaleMetricsTable.executionCount + execTimeMs) /
                    (GrayscaleMetricsTable.executionCount + 1)
                statement[GrayscaleMetricsTable.hitCount] =
                    GrayscaleMetricsTable.hitCount + (if (isSuccess) 1 else 0)
                statement[GrayscaleMetricsTable.errorCount] =
                    GrayscaleMetricsTable.errorCount + (if (isSuccess) 0 else 1)
            }
        }
}
