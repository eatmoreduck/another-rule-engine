package com.example.ruleengine.controller;

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

/**
 * 灰度发布管理 REST API 控制器
 * VER-03: 灰度发布
 * VER-04: 灰度效果对比
 */
@RestController
@RequestMapping("/api/v1/grayscale")
@RequiredArgsConstructor
@Slf4j
public class GrayscaleController {

    private final GrayscaleService grayscaleService;

    /**
     * 创建灰度配置
     * POST /api/v1/grayscale
     */
    @PostMapping
    public ResponseEntity<GrayscaleConfigResponse> createGrayscale(
            @Valid @RequestBody CreateGrayscaleRequest request,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        log.info("创建灰度配置: ruleKey={}, grayscaleVersion={}, operator={}",
                request.getRuleKey(), request.getGrayscaleVersion(), operator);
        GrayscaleConfigResponse response =
                grayscaleService.createGrayscaleConfig(request, operator);
        return ResponseEntity.ok(response);
    }

    /**
     * 启动灰度
     * PUT /api/v1/grayscale/{id}/start
     */
    @PutMapping("/{id}/start")
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
    public ResponseEntity<List<GrayscaleConfigResponse>> getGrayscaleConfigs(
            @PathVariable String ruleKey) {
        log.info("获取规则灰度配置列表: ruleKey={}", ruleKey);
        List<GrayscaleConfigResponse> configs =
                grayscaleService.getGrayscaleConfigs(ruleKey);
        return ResponseEntity.ok(configs);
    }
}
