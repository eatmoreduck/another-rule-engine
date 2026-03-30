package com.example.ruleengine.controller;

import com.example.ruleengine.model.dto.ConflictResult;
import com.example.ruleengine.service.test.RuleConflictDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 规则冲突检测 REST API 控制器
 * TEST-03: 提供冲突检测接口
 */
@RestController
@RequestMapping("/api/v1/conflicts")
@RequiredArgsConstructor
@Slf4j
public class ConflictDetectionController {

    private final RuleConflictDetector conflictDetector;

    /**
     * 全量冲突检测
     * POST /api/v1/conflicts/detect
     */
    @PostMapping("/detect")
    public ResponseEntity<List<ConflictResult>> detectAllConflicts() {
        log.info("执行全量冲突检测");
        List<ConflictResult> conflicts = conflictDetector.detectAllConflicts();
        return ResponseEntity.ok(conflicts);
    }

    /**
     * 单规则冲突检测
     * GET /api/v1/conflicts/rule/{ruleKey}
     */
    @GetMapping("/rule/{ruleKey}")
    public ResponseEntity<List<ConflictResult>> detectConflictsForRule(
            @PathVariable String ruleKey) {
        log.info("执行单规则冲突检测: ruleKey={}", ruleKey);
        List<ConflictResult> conflicts = conflictDetector.detectConflictsForRule(ruleKey);
        return ResponseEntity.ok(conflicts);
    }
}
