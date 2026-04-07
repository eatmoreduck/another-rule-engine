package com.example.ruleengine.controller;

import com.example.ruleengine.config.FeatureProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 功能开关 API
 * 前端通过此接口获取当前启用的功能列表
 */
@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureProperties featureProperties;

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getFeatures() {
        return ResponseEntity.ok(Map.of(
                "multiEnvironment", featureProperties.getMultiEnvironment().isEnabled(),
                "importExport", featureProperties.getImportExport().isEnabled()
        ));
    }
}
