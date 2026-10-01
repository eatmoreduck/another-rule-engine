package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.dto.ExecutionStatsResponse
import com.eatmoreduck.ruleengine.admin.monitoring.MetricsQueryService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 规则执行监控指标 REST API 控制器（路径/参数/响应字段与旧 MetricsController 一致：
 * /api/v1/metrics 族，类级登录校验，无方法级权限码）。
 */
@RestController
@RequestMapping("/api/v1/metrics")
@SaCheckLogin
class MetricsController(
    private val metricsQueryService: MetricsQueryService,
) {
    private val log = LoggerFactory.getLogger(MetricsController::class.java)

    /** 监控总览：GET /api/v1/metrics/overview */
    @GetMapping("/overview")
    fun getMetricsOverview(): ResponseEntity<Map<String, Any>> {
        log.debug("获取监控总览数据")
        return ResponseEntity.ok(metricsQueryService.getOverview())
    }

    /** 规则执行排行：GET /api/v1/metrics/rules?sortBy=executionCount&sortOrder=desc&limit=10 */
    @GetMapping("/rules")
    fun getRuleMetricsRanking(
        @RequestParam(defaultValue = "executionCount") sortBy: String,
        @RequestParam(defaultValue = "desc") sortOrder: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ResponseEntity<List<Map<String, Any>>> {
        log.debug("获取规则执行排行: sortBy={}, sortOrder={}, limit={}", sortBy, sortOrder, limit)
        return ResponseEntity.ok(metricsQueryService.getRuleRanking(sortBy, sortOrder, limit))
    }

    /** 指定规则的执行统计：GET /api/v1/metrics/rules/{ruleKey} */
    @GetMapping("/rules/{ruleKey}")
    fun getRuleMetrics(
        @PathVariable ruleKey: String,
    ): ResponseEntity<ExecutionStatsResponse> {
        log.debug("Fetching execution stats for rule: {}", ruleKey)
        return ResponseEntity.ok(metricsQueryService.getRuleStats(ruleKey))
    }
}
