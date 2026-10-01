package com.eatmoreduck.ruleengine.admin.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 功能开关 API（路径/键名与旧 FeatureController 一致：GET /api/v1/features，
 * 键为 multiEnvironment / importExport）。
 *
 * 与旧实现的差异说明：旧值来自 FeatureProperties 配置（当时两项均可配）；
 * 新后端尚未实现多环境隔离与导入导出功能，固定返回 false/false
 * （MainLayout 依据这两个开关隐藏对应菜单）。
 *
 * 鉴权口径与旧一致：控制器无注解，由 SaTokenConfig 对所有 /api/v1 前缀路径的登录拦截兜底。
 */
@RestController
@RequestMapping("/api/v1/features")
class FeatureController {
    /** 功能开关列表：GET /api/v1/features */
    @GetMapping
    fun getFeatures(): ResponseEntity<Map<String, Boolean>> =
        ResponseEntity.ok(
            linkedMapOf(
                "multiEnvironment" to false,
                "importExport" to false,
            ),
        )
}
