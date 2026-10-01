package com.eatmoreduck.ruleengine.admin.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckPermission
import com.eatmoreduck.ruleengine.admin.dto.CreateGrayscaleRequest
import com.eatmoreduck.ruleengine.admin.dto.GrayscaleConfigResponse
import com.eatmoreduck.ruleengine.admin.dto.GrayscaleReportResponse
import com.eatmoreduck.ruleengine.admin.dto.PageResponse
import com.eatmoreduck.ruleengine.admin.grayscale.GrayscaleService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 灰度发布管理 REST API 控制器（路径/方法/权限码与旧 GrayscaleController 一致：/api/v1/grayscale 全族路径）。。
 */
@RestController
@RequestMapping("/api/v1/grayscale")
@SaCheckLogin
class GrayscaleController(
    private val grayscaleService: GrayscaleService,
) {
    private val log = LoggerFactory.getLogger(GrayscaleController::class.java)

    /** 查询灰度配置列表（分页）：GET /api/v1/grayscale */
    @GetMapping
    @SaCheckPermission("api:grayscale:view")
    fun listGrayscales(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) ruleKey: String?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<GrayscaleConfigResponse>> {
        log.info(
            "查询灰度列表: status={}, ruleKey={}, targetType={}, page={}, size={}",
            status,
            ruleKey,
            targetType,
            page,
            size,
        )
        val allConfigs = grayscaleService.listGrayscaleConfigs(status, ruleKey, targetType)
        return ResponseEntity.ok(PageResponse.of(allConfigs, page, size))
    }

    /** 创建灰度配置：POST /api/v1/grayscale */
    @PostMapping
    @SaCheckPermission("api:grayscale:manage")
    fun createGrayscale(
        @Valid @RequestBody request: CreateGrayscaleRequest,
        @RequestHeader(value = "X-Operator", defaultValue = "system") operator: String,
    ): ResponseEntity<GrayscaleConfigResponse> {
        val targetType = request.targetType ?: "RULE"
        val targetKey = request.targetKey ?: request.ruleKey
        log.info(
            "创建灰度配置: targetType={}, targetKey={}, grayscaleVersion={}, operator={}",
            targetType,
            targetKey,
            request.grayscaleVersion,
            operator,
        )
        return ResponseEntity.ok(grayscaleService.createGrayscaleConfig(request, operator))
    }

    /** 启动灰度：PUT /api/v1/grayscale/{id}/start */
    @PutMapping("/{id}/start")
    @SaCheckPermission("api:grayscale:manage")
    fun startGrayscale(
        @PathVariable id: Long,
    ): ResponseEntity<GrayscaleConfigResponse> {
        log.info("启动灰度: id={}", id)
        return ResponseEntity.ok(grayscaleService.startGrayscale(id))
    }

    /** 暂停灰度：PUT /api/v1/grayscale/{id}/pause */
    @PutMapping("/{id}/pause")
    @SaCheckPermission("api:grayscale:manage")
    fun pauseGrayscale(
        @PathVariable id: Long,
    ): ResponseEntity<GrayscaleConfigResponse> {
        log.info("暂停灰度: id={}", id)
        return ResponseEntity.ok(grayscaleService.pauseGrayscale(id))
    }

    /** 完成灰度（全量切换）：PUT /api/v1/grayscale/{id}/complete */
    @PutMapping("/{id}/complete")
    @SaCheckPermission("api:grayscale:manage")
    fun completeGrayscale(
        @PathVariable id: Long,
    ): ResponseEntity<GrayscaleConfigResponse> {
        log.info("完成灰度: id={}", id)
        return ResponseEntity.ok(grayscaleService.completeGrayscale(id))
    }

    /** 回滚灰度：PUT /api/v1/grayscale/{id}/rollback */
    @PutMapping("/{id}/rollback")
    @SaCheckPermission("api:grayscale:manage")
    fun rollbackGrayscale(
        @PathVariable id: Long,
    ): ResponseEntity<GrayscaleConfigResponse> {
        log.info("回滚灰度: id={}", id)
        return ResponseEntity.ok(grayscaleService.rollbackGrayscale(id))
    }

    /** 灰度对比报告：GET /api/v1/grayscale/{id}/report */
    @GetMapping("/{id}/report")
    @SaCheckPermission("api:grayscale:view")
    fun getGrayscaleReport(
        @PathVariable id: Long,
    ): ResponseEntity<GrayscaleReportResponse> {
        log.info("获取灰度对比报告: id={}", id)
        return ResponseEntity.ok(grayscaleService.getGrayscaleReport(id))
    }

    /** 规则的全部灰度配置：GET /api/v1/grayscale/rule/{ruleKey} */
    @GetMapping("/rule/{ruleKey}")
    @SaCheckPermission("api:grayscale:view")
    fun getGrayscaleConfigs(
        @PathVariable ruleKey: String,
    ): ResponseEntity<List<GrayscaleConfigResponse>> {
        log.info("获取规则灰度配置列表: ruleKey={}", ruleKey)
        return ResponseEntity.ok(grayscaleService.getGrayscaleConfigs(ruleKey))
    }
}
