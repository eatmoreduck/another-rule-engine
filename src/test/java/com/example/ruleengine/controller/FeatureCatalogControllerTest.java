package com.example.ruleengine.controller;

import com.example.ruleengine.domain.FeatureAlias;
import com.example.ruleengine.domain.FeatureDefinition;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.FeatureAliasRepository;
import com.example.ruleengine.repository.FeatureDefinitionRepository;
import com.example.ruleengine.repository.RuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("FeatureCatalogController 集成测试")
class FeatureCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeatureDefinitionRepository featureDefinitionRepository;

    @Autowired
    private FeatureAliasRepository featureAliasRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @BeforeEach
    void setUp() {
        featureAliasRepository.deleteAllInBatch();
        featureDefinitionRepository.deleteAllInBatch();
        ruleRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("应创建特征并返回别名列表")
    void shouldCreateFeatureDefinitionWithAliases() throws Exception {
        Map<String, Object> request = Map.of(
                "code", "device_risk_score",
                "name", "设备风险分",
                "dataType", "NUMBER",
                "sourceType", "MODEL",
                "sensitivity", "SENSITIVE",
                "status", "ACTIVE",
                "aliases", List.of("device_score", "risk_device_score")
        );

        mockMvc.perform(post("/api/v1/features/catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("device_risk_score"))
                .andExpect(jsonPath("$.aliases", hasSize(2)))
                .andExpect(jsonPath("$.aliases", hasItem("device_score")));
    }

    @Test
    @DisplayName("应识别别名映射并返回类型告警")
    void shouldValidateAliasMappingAndTypeWarnings() throws Exception {
        featureDefinitionRepository.save(FeatureDefinition.builder()
                .code("order_amount")
                .name("订单金额")
                .dataType("NUMBER")
                .sourceType("INPUT")
                .sensitivity("NORMAL")
                .status("ACTIVE")
                .build());
        featureAliasRepository.save(FeatureAlias.builder()
                .aliasCode("amount")
                .canonicalCode("order_amount")
                .aliasType("LEGACY")
                .status("ACTIVE")
                .build());

        Map<String, Object> request = Map.of(
                "items", List.of(Map.of(
                        "fieldName", "amount",
                        "operator", "GT",
                        "threshold", "abc"
                ))
        );

        mockMvc.perform(post("/api/v1/features/catalog/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.items[0].matchedByAlias").value(true))
                .andExpect(jsonPath("$.items[0].canonicalCode").value("order_amount"))
                .andExpect(jsonPath("$.items[0].warnings", hasItem("阈值类型不匹配，应为数值")));
    }

    @Test
    @DisplayName("应返回引用该特征的规则")
    void shouldReturnFeatureReferences() throws Exception {
        featureDefinitionRepository.save(FeatureDefinition.builder()
                .code("risk_score")
                .name("风险分")
                .dataType("NUMBER")
                .sourceType("MODEL")
                .sensitivity("SENSITIVE")
                .status("ACTIVE")
                .build());
        ruleRepository.save(Rule.builder()
                .ruleKey("risk_score_check")
                .ruleName("风险分检查")
                .groovyScript("if (features.risk_score > 0.8) { return [decision: 'REJECT', reason: 'high risk'] }\nreturn [decision: 'PASS', reason: 'ok']")
                .createdBy("tester")
                .enabled(true)
                .deleted(false)
                .version(1)
                .build());

        mockMvc.perform(get("/api/v1/features/catalog/risk_score/references"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("rule"))
                .andExpect(jsonPath("$[0].key").value("risk_score_check"));
    }
}
