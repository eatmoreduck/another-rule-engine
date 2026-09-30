package com.example.ruleengine.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.ruleengine.model.dto.FeatureDefinitionRequest;
import com.example.ruleengine.model.dto.FeatureDefinitionResponse;
import com.example.ruleengine.model.dto.FeatureValidationRequest;
import com.example.ruleengine.model.dto.FeatureValidationResponse;
import com.example.ruleengine.model.dto.RuleReference;
import com.example.ruleengine.service.feature.FeatureCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/features/catalog")
@RequiredArgsConstructor
@SaCheckLogin
public class FeatureCatalogController {

    private final FeatureCatalogService featureCatalogService;

    @GetMapping
    @SaCheckPermission("api:feature-catalog:view")
    public ResponseEntity<Page<FeatureDefinitionResponse>> listDefinitions(
            Pageable pageable,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(featureCatalogService.searchDefinitions(
                keyword,
                scope,
                dataType,
                sourceType,
                sensitivity,
                status,
                pageable
        ));
    }

    @GetMapping("/{code}")
    @SaCheckPermission("api:feature-catalog:view")
    public ResponseEntity<FeatureDefinitionResponse> getDefinition(@PathVariable String code) {
        return ResponseEntity.ok(featureCatalogService.getDefinition(code));
    }

    @PostMapping
    @SaCheckPermission("api:feature-catalog:manage")
    public ResponseEntity<FeatureDefinitionResponse> createDefinition(@Valid @RequestBody FeatureDefinitionRequest request) {
        return ResponseEntity.ok(featureCatalogService.createDefinition(request));
    }

    @PutMapping("/{code}")
    @SaCheckPermission("api:feature-catalog:manage")
    public ResponseEntity<FeatureDefinitionResponse> updateDefinition(
            @PathVariable String code,
            @Valid @RequestBody FeatureDefinitionRequest request) {
        return ResponseEntity.ok(featureCatalogService.updateDefinition(code, request));
    }

    @PostMapping("/validate")
    @SaCheckPermission("api:feature-catalog:view")
    public ResponseEntity<FeatureValidationResponse> validate(@Valid @RequestBody FeatureValidationRequest request) {
        return ResponseEntity.ok(featureCatalogService.validate(request));
    }

    @GetMapping("/{code}/references")
    @SaCheckPermission("api:feature-catalog:view")
    public ResponseEntity<List<RuleReference>> getReferences(@PathVariable String code) {
        return ResponseEntity.ok(featureCatalogService.getReferences(code));
    }
}
