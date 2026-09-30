package com.example.ruleengine.admin.grayscale

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository

/** 灰度指标行（对比报告所需的最小列集） */
data class GrayscaleMetricRow(
    val version: Int,
    val executionCount: Int,
    val hitCount: Int,
    val errorCount: Int,
    val avgExecutionTimeMs: Int,
)

/**
 * 灰度指标读写（对应旧 GrayscaleMetricRepository 的最小职责）：
 * - 创建灰度配置时初始化当前/灰度两个版本的零值指标行（旧 initMetrics）；
 * - 对比报告读取指标行（旧 getGrayscaleReport）。
 *
 * decision-api 的执行计数写入属于阶段 3；本批只承担初始化与读取，
 * 表对象列集与迁移基线 V6（grayscale_metrics）一致。
 */
interface GrayscaleMetricsRepository {
    /** 初始化某配置两个版本的零值指标（旧 initMetrics；重复初始化跳过） */
    fun initMetrics(
        configId: Long,
        currentVersion: Int,
        grayscaleVersion: Int,
    )

    /** 某配置的全部指标行 */
    fun findByConfigId(configId: Long): List<GrayscaleMetricRow>
}

/** [GrayscaleMetricsRepository] 的 Exposed 实现 */
@Repository
class ExposedGrayscaleMetricsRepository : GrayscaleMetricsRepository {
    override fun initMetrics(
        configId: Long,
        currentVersion: Int,
        grayscaleVersion: Int,
    ) {
        listOf(currentVersion, grayscaleVersion).forEach { version ->
            val exists =
                GrayscaleMetricsTable
                    .selectAll()
                    .where {
                        (GrayscaleMetricsTable.configId eq configId) and (GrayscaleMetricsTable.version eq version)
                    }.any()
            if (!exists) {
                GrayscaleMetricsTable.insert { statement ->
                    statement[GrayscaleMetricsTable.configId] = configId
                    statement[GrayscaleMetricsTable.version] = version
                    statement[executionCount] = 0
                    statement[hitCount] = 0
                    statement[errorCount] = 0
                    statement[avgExecutionTimeMs] = 0
                }
            }
        }
    }

    override fun findByConfigId(configId: Long): List<GrayscaleMetricRow> =
        GrayscaleMetricsTable
            .selectAll()
            .where { GrayscaleMetricsTable.configId eq configId }
            .map { row ->
                GrayscaleMetricRow(
                    version = row[GrayscaleMetricsTable.version],
                    executionCount = row[GrayscaleMetricsTable.executionCount] ?: 0,
                    hitCount = row[GrayscaleMetricsTable.hitCount] ?: 0,
                    errorCount = row[GrayscaleMetricsTable.errorCount] ?: 0,
                    avgExecutionTimeMs = row[GrayscaleMetricsTable.avgExecutionTimeMs] ?: 0,
                )
            }

    /** grayscale_metrics 最小表对象（列与 V6 基线一致） */
    private object GrayscaleMetricsTable : Table("grayscale_metrics") {
        val configId = long("grayscale_config_id")
        val version = integer("version")
        val executionCount = integer("execution_count").nullable()
        val hitCount = integer("hit_count").nullable()
        val errorCount = integer("error_count").nullable()
        val avgExecutionTimeMs = integer("avg_execution_time_ms").nullable()
    }
}
