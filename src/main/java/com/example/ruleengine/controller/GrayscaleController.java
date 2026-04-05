package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.model.dto.CreateGrayscaleRequest;
import com.example.ruleengine.model.dto.GrayscaleConfigResponse;
import com.example.ruleengine.model.dto.GrayscaleReportResponse;
import com.example.ruleengine.service.grayscale.GrayscaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 灰度发布管理 REST API 控制器
 * VER-03: 灰度发布
 * VER-04: 灰度效果对比
 */
@RestController
@RequestMapping("/api/v1/grayscale")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class GrayscaleController {

    private final GrayscaleService grayscaleService;

    /**
     * 查询灰度配置列表（分页）
     * GET /api/v1/grayscale
     *
     * @param status     状态过滤（可选）
     * @param ruleKey    规则Key过滤（可选）
     * @param targetType 目标类型过滤（可选，RULE/DECISION_FLOW）
     * @param page       页码（从0开始）
     * @param size       每页大小
     * @return 分页灰度配置列表
     */
    @GetMapping
    @SaCheckPermission("api:grayscale:view")
    public ResponseEntity<Map<String, Object>> listGrayscales(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ruleKey,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("查询灰度列表: status={}, ruleKey={}, targetType={}, page={}, size={}",
                status, ruleKey, targetType, page, size);

        List<GrayscaleConfigResponse> allConfigs = grayscaleService.listGrayscaleConfigs(status, ruleKey, targetType);

        // 分页处理
        int totalElements = allConfigs.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<GrayscaleConfigResponse> content = allConfigs.subList(fromIndex, toIndex);

        Map<String, Object> pageResponse = Map.of(
                "content", content,
                "totalElements", totalElements,
                "totalPages", totalPages,
                "number", page,
                "size", size
        );

        return ResponseEntity.ok(pageResponse);
    }

    /**
     * 创建灰度配置（支持规则和决策流）
     * POST /api/v1/grayscale
     */
    @PostMapping
    @SaCheckPermission("api:grayscale:manage")
    public ResponseEntity<GrayscaleConfigResponse> createGrayscale(
            @Valid @RequestBody CreateGrayscaleRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        String targetType = request.getTargetType() != null ? request.getTargetType() : "RULE";
        String targetKey = request.getTargetKey() != null ? request.getTargetKey() : request.getRuleKey();
        log.info("创建灰度配置: targetType={}, targetKey={}, grayscaleVersion={}, operator={}",
                targetType, targetKey, request.getGrayscaleVersion(), operator);
        GrayscaleConfigResponse response =
                grayscaleService.createGrayscaleConfig(request, operator);
        return ResponseEntity.ok(response);
    }

    /**
     * 启动灰度
     * PUT /api/v1/grayscale/{id}/start
     */
    @PutMapping("/{id}/start")
    @SaCheckPermission("api:grayscale:manage")
    public ResponseEntity<GrayscaleConfigResponse> startGrayscale(
            @PathVariable Long id) {
        log.info("启动灰度: id={}", id);
        GrayscaleConfigResponse response =
                grayscaleService.startGrayscale(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 暂停灰度
     * PUT /api/v1/grayscale/{id}/pause
     */
    @PutMapping("/{id}/pause")
    @SaCheckPermission("api:grayscale:manage")
    public ResponseEntity<GrayscaleConfigResponse> pauseGrayscale(
            @PathVariable Long id) {
        log.info("暂停灰度: id={}", id);
        GrayscaleConfigResponse response =
                grayscaleService.pauseGrayscale(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 完成灰度（全量切换）
     * PUT /api/v1/grayscale/{id}/complete
     */
    @PutMapping("/{id}/complete")
    @SaCheckPermission("api:grayscale:manage")
    public ResponseEntity<GrayscaleConfigResponse> completeGrayscale(
            @PathVariable Long id) {
        log.info("完成灰度: id={}", id);
        GrayscaleConfigResponse response =
                grayscaleService.completeGrayscale(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 回滚灰度
     * PUT /api/v1/grayscale/{id}/rollback
     */
    @PutMapping("/{id}/rollback")
    @SaCheckPermission("api:grayscale:manage")
    public ResponseEntity<GrayscaleConfigResponse> rollbackGrayscale(
            @PathVariable Long id) {
        log.info("回滚灰度: id={}", id);
        GrayscaleConfigResponse response =
                grayscaleService.rollbackGrayscale(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取灰度对比报告
     * GET /api/v1/grayscale/{id}/report
     */
    @GetMapping("/{id}/report")
    @SaCheckPermission("api:grayscale:view")
    public ResponseEntity<GrayscaleReportResponse> getGrayscaleReport(
            @PathVariable Long id) {
        log.info("获取灰度对比报告: id={}", id);
        GrayscaleReportResponse report =
                grayscaleService.getGrayscaleReport(id);
        return ResponseEntity.ok(report);
    }

    /**
     * 获取规则的所有灰度配置
     * GET /api/v1/grayscale/rule/{ruleKey}
     */
    @GetMapping("/rule/{ruleKey}")
    @SaCheckPermission("api:grayscale:view")
    public ResponseEntity<List<GrayscaleConfigResponse>> getGrayscaleConfigs(
            @PathVariable String ruleKey) {
        log.info("获取规则灰度配置列表: ruleKey={}", ruleKey);
        List<GrayscaleConfigResponse> configs =
                grayscaleService.getGrayscaleConfigs(ruleKey);
        return ResponseEntity.ok(configs);
    }
}
