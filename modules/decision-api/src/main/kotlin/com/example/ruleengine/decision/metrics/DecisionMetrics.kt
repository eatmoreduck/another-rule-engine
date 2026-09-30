package com.example.ruleengine.decision.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 决策耗时与错误指标（阶段 6 压测的数据来源）。
 *
 * - decision.duration：Timer，标签 action（PASS/REJECT/MANUAL_REVIEW）+ version（钉住版本号），
 *   阶段 6 按此聚合 p50/p95 与动作分布；
 * - decision.errors：Counter，标签 target + phase（resolve/execute）。
 */
@Component
class DecisionMetrics(
    private val registry: MeterRegistry,
) {
    fun recordDuration(
        target: String,
        action: String,
        version: Int,
        elapsedMs: Long,
    ) {
        Timer
            .builder("decision.duration")
            .tag("target", target)
            .tag("action", action)
            .tag("version", version.toString())
            .register(registry)
            .record(elapsedMs, TimeUnit.MILLISECONDS)
    }

    fun recordError(
        target: String,
        phase: String,
    ) {
        registry
            .counter("decision.errors", "target", target, "phase", phase)
            .increment()
    }
}
