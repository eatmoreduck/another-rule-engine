package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.eatmoreduck.ruleengine.admin.analytics.RuleAnalyticsService
import com.eatmoreduck.ruleengine.admin.analytics.RuleDependencyAnalyzer
import com.eatmoreduck.ruleengine.admin.dto.DependencyGraphResponse
import com.eatmoreduck.ruleengine.admin.dto.RuleAnalyticsResponse
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 规则效果分析 REST API 控制器（路径/参数/响应字段与旧 AnalyticsController 一致：
 * /api/v1/analytics 族，类级登录校验，无方法级权限码；日期参数缺省最近 7 天）。
 */
@RestController
@RequestMapping("/api/v1/analytics")
@SaCheckLogin
class AnalyticsController(
    private val analyticsService: RuleAnalyticsService,
    private val dependencyAnalyzer: RuleDependencyAnalyzer,
) {
    private val log = LoggerFactory.getLogger(AnalyticsController::class.java)

    /** 规则效果分析：GET /api/v1/analytics/rules/{ruleKey}?startDate=&endDate= */
    @GetMapping("/rules/{ruleKey}")
    fun getRuleAnalytics(
        @PathVariable ruleKey: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): ResponseEntity<RuleAnalyticsResponse> {
        val start = startDate ?: LocalDate.now().minusDays(7)
        val end = endDate ?: LocalDate.now()
        log.info("获取规则效果分析: ruleKey={}, startDate={}, endDate={}", ruleKey, start, end)
        return ResponseEntity.ok(analyticsService.getAnalytics(ruleKey, start, end))
    }

    /** 全局概览：GET /api/v1/analytics/overview?startDate=&endDate= */
    @GetMapping("/overview")
    fun getOverview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): ResponseEntity<List<RuleAnalyticsResponse>> {
        val start = startDate ?: LocalDate.now().minusDays(7)
        val end = endDate ?: LocalDate.now()
        log.info("获取全局分析概览: startDate={}, endDate={}", start, end)
        return ResponseEntity.ok(analyticsService.getOverview(start, end))
    }

    /** 依赖关系分析：GET /api/v1/analytics/dependencies */
    @GetMapping("/dependencies")
    fun getDependencyGraph(): ResponseEntity<DependencyGraphResponse> {
        log.info("获取规则依赖关系图")
        return ResponseEntity.ok(dependencyAnalyzer.analyzeDependencies())
    }

    /** 单规则依赖关系分析：GET /api/v1/analytics/dependencies/{ruleKey} */
    @GetMapping("/dependencies/{ruleKey}")
    fun getRuleDependencies(
        @PathVariable ruleKey: String,
    ): ResponseEntity<DependencyGraphResponse> {
        log.info("获取规则依赖关系: ruleKey={}", ruleKey)
        return ResponseEntity.ok(dependencyAnalyzer.analyzeRuleDependencies(ruleKey))
    }
}
