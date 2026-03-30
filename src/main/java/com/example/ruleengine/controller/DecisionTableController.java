package com.example.ruleengine.controller;

import com.example.ruleengine.model.dto.DecisionTableRequest;
import com.example.ruleengine.model.dto.DecisionTableResponse;
import com.example.ruleengine.model.dto.DecisionTableValidateResponse;
import com.example.ruleengine.service.decisiontable.DecisionTableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 决策表 REST API 控制器
 */
@RestController
@RequestMapping("/api/v1/decision-table")
@RequiredArgsConstructor
@Slf4j
public class DecisionTableController {

    private final DecisionTableService decisionTableService;

    /**
     * 决策表转 Groovy DSL 脚本
     * POST /api/v1/decision-table/convert
     */
    @PostMapping("/convert")
    public ResponseEntity<DecisionTableResponse> convertToGroovy(
            @Valid @RequestBody DecisionTableRequest request) {
        log.info("决策表转Groovy脚本: ruleName={}", request.getRuleName());
        DecisionTableResponse response = decisionTableService.convertToGroovy(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 验证决策表
     * POST /api/v1/decision-table/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<DecisionTableValidateResponse> validateDecisionTable(
            @Valid @RequestBody DecisionTableRequest request) {
        log.info("验证决策表");
        DecisionTableValidateResponse response = decisionTableService.validate(request);
        return ResponseEntity.ok(response);
    }
}
