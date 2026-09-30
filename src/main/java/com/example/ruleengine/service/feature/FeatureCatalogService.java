package com.example.ruleengine.service.feature;

import com.example.ruleengine.domain.DecisionFlow;
import com.example.ruleengine.domain.FeatureAlias;
import com.example.ruleengine.domain.FeatureDefinition;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.model.dto.FeatureDefinitionRequest;
import com.example.ruleengine.model.dto.FeatureDefinitionResponse;
import com.example.ruleengine.model.dto.FeatureValidationRequest;
import com.example.ruleengine.model.dto.FeatureValidationResponse;
import com.example.ruleengine.model.dto.RuleReference;
import com.example.ruleengine.repository.DecisionFlowRepository;
import com.example.ruleengine.repository.FeatureAliasRepository;
import com.example.ruleengine.repository.FeatureDefinitionRepository;
import com.example.ruleengine.repository.RuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureCatalogService {

    private static final Pattern FEATURE_PATTERN = Pattern.compile("features\\.(\\w+)");
    private static final Set<String> NUMERIC_TYPES = Set.of("NUMBER", "INTEGER", "LONG", "DOUBLE", "DECIMAL");
    private static final Set<String> TEXT_TYPES = Set.of("STRING", "TEXT", "LIST", "ARRAY");
    private static final Set<String> BOOLEAN_TYPES = Set.of("BOOLEAN");

    private final FeatureDefinitionRepository featureDefinitionRepository;
    private final FeatureAliasRepository featureAliasRepository;
    private final RuleRepository ruleRepository;
    private final DecisionFlowRepository decisionFlowRepository;
    private final ObjectMapper objectMapper;

    public Page<FeatureDefinitionResponse> searchDefinitions(
            String keyword,
            String scope,
            String dataType,
            String sourceType,
            String sensitivity,
            String status,
            Pageable pageable) {
        Page<FeatureDefinition> page = featureDefinitionRepository.search(
                normalizeFilter(keyword),
                normalizeFilter(scope),
                normalizeEnumFilter(dataType),
                normalizeEnumFilter(sourceType),
                normalizeEnumFilter(sensitivity),
                normalizeEnumFilter(status),
                pageable
        );

        Map<String, List<String>> aliasesByCode = loadAliasesByCanonicalCode(
                page.getContent().stream().map(FeatureDefinition::getCode).toList()
        );
        return page.map(feature -> toResponse(feature, aliasesByCode.getOrDefault(feature.getCode(), List.of())));
    }

    public List<FeatureDefinitionResponse> searchActiveDefinitions(String keyword, int limit) {
        Pageable pageable = Pageable.ofSize(Math.max(1, limit));
        return searchDefinitions(keyword, null, null, null, null, "ACTIVE", pageable).getContent();
    }

    public FeatureDefinitionResponse getDefinition(String code) {
        FeatureDefinition feature = getFeatureByCode(code);
        return toResponse(feature, listAliases(feature.getCode()));
    }

    @Transactional
    public FeatureDefinitionResponse createDefinition(FeatureDefinitionRequest request) {
        String code = normalizeCode(request.getCode());
        if (featureDefinitionRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("特征编码已存在: " + code);
        }

        validateAliasOwnership(code, request.getAliases(), null);

        FeatureDefinition feature = FeatureDefinition.builder()
                .code(code)
                .name(request.getName().trim())
                .dataType(normalizeRequiredEnum(request.getDataType(), "特征类型不能为空"))
                .sourceType(normalizeRequiredEnum(request.getSourceType(), "特征来源不能为空"))
                .exampleValue(trimToNull(request.getExampleValue()))
                .description(trimToNull(request.getDescription()))
                .scope(trimToNull(request.getScope()))
                .sensitivity(normalizeOptionalEnum(request.getSensitivity(), "NORMAL"))
                .status(normalizeOptionalEnum(request.getStatus(), "ACTIVE"))
                .owner(trimToNull(request.getOwner()))
                .build();

        FeatureDefinition saved = featureDefinitionRepository.save(feature);
        replaceAliases(saved.getCode(), request.getAliases());
        return toResponse(saved, listAliases(saved.getCode()));
    }

    @Transactional
    public FeatureDefinitionResponse updateDefinition(String code, FeatureDefinitionRequest request) {
        FeatureDefinition existing = getFeatureByCode(code);
        String targetCode = normalizeCode(code);
        if (!targetCode.equalsIgnoreCase(normalizeCode(request.getCode()))) {
            throw new IllegalArgumentException("特征编码不允许修改");
        }

        validateAliasOwnership(targetCode, request.getAliases(), targetCode);

        existing.setName(request.getName().trim());
        existing.setDataType(normalizeRequiredEnum(request.getDataType(), "特征类型不能为空"));
        existing.setSourceType(normalizeRequiredEnum(request.getSourceType(), "特征来源不能为空"));
        existing.setExampleValue(trimToNull(request.getExampleValue()));
        existing.setDescription(trimToNull(request.getDescription()));
        existing.setScope(trimToNull(request.getScope()));
        existing.setSensitivity(normalizeOptionalEnum(request.getSensitivity(), "NORMAL"));
        existing.setStatus(normalizeOptionalEnum(request.getStatus(), "ACTIVE"));
        existing.setOwner(trimToNull(request.getOwner()));

        FeatureDefinition saved = featureDefinitionRepository.save(existing);
        replaceAliases(saved.getCode(), request.getAliases());
        return toResponse(saved, listAliases(saved.getCode()));
    }

    public FeatureValidationResponse validate(FeatureValidationRequest request) {
        List<FeatureValidationResponse.ItemResult> itemResults = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> unknownFields = new ArrayList<>();
        boolean valid = true;

        for (FeatureValidationRequest.Item item : request.getItems()) {
            Resolution resolution = resolve(item.getFieldName());
            List<String> itemWarnings = new ArrayList<>();

            if (resolution.definition() == null) {
                String unknown = normalizeCode(item.getFieldName());
                unknownFields.add(unknown);
                itemWarnings.add("字段未收录于特征字典");
                warnings.add(String.format(Locale.ROOT, "字段 %s 未收录于特征字典", unknown));
                valid = false;
                itemResults.add(FeatureValidationResponse.ItemResult.builder()
                        .fieldName(unknown)
                        .found(false)
                        .matchedByAlias(false)
                        .warnings(itemWarnings)
                        .build());
                continue;
            }

            FeatureDefinition definition = resolution.definition();
            if (resolution.alias() != null) {
                itemWarnings.add(String.format(Locale.ROOT, "字段 %s 已映射为标准特征 %s", normalizeCode(item.getFieldName()), definition.getCode()));
            }
            if (!"ACTIVE".equalsIgnoreCase(definition.getStatus())) {
                itemWarnings.add(String.format(Locale.ROOT, "特征 %s 当前状态为 %s", definition.getCode(), definition.getStatus()));
            }

            itemWarnings.addAll(checkOperatorCompatibility(definition.getDataType(), item.getOperator(), item.getThreshold()));
            if (!itemWarnings.isEmpty()) {
                warnings.addAll(itemWarnings);
            }
            if (itemWarnings.stream().anyMatch(msg -> msg.contains("不匹配") || msg.contains("应为") || msg.contains("未收录"))) {
                valid = false;
            }

            itemResults.add(FeatureValidationResponse.ItemResult.builder()
                    .fieldName(normalizeCode(item.getFieldName()))
                    .found(true)
                    .matchedByAlias(resolution.alias() != null)
                    .canonicalCode(definition.getCode())
                    .matchedAlias(resolution.alias() != null ? resolution.alias().getAliasCode() : null)
                    .dataType(definition.getDataType())
                    .sourceType(definition.getSourceType())
                    .sensitivity(definition.getSensitivity())
                    .warnings(itemWarnings)
                    .build());
        }

        return FeatureValidationResponse.builder()
                .valid(valid)
                .warnings(warnings)
                .unknownFields(unknownFields)
                .items(itemResults)
                .build();
    }

    public FeatureValidationResponse validateGroovyScript(String groovyScript) {
        List<FeatureValidationRequest.Item> items = extractFieldsFromGroovyScript(groovyScript).stream()
                .map(field -> FeatureValidationRequest.Item.builder().fieldName(field).build())
                .toList();
        return validate(FeatureValidationRequest.builder().items(items).build());
    }

    public List<RuleReference> getReferences(String code) {
        FeatureDefinition definition = getFeatureByCode(code);
        Set<String> acceptedNames = new LinkedHashSet<>();
        acceptedNames.add(definition.getCode());
        acceptedNames.addAll(listAliases(definition.getCode()));

        Map<String, RuleReference> references = new LinkedHashMap<>();
        for (Rule rule : ruleRepository.findByDeletedFalse()) {
            Set<String> fields = extractFieldsFromGroovyScript(rule.getGroovyScript());
            if (intersects(fields, acceptedNames)) {
                references.put("rule:" + rule.getRuleKey(), RuleReference.builder()
                        .type("rule")
                        .id(rule.getId())
                        .name(rule.getRuleName())
                        .key(rule.getRuleKey())
                        .build());
            }
        }

        for (DecisionFlow flow : decisionFlowRepository.findAll()) {
            Set<String> fields = extractFieldsFromFlowGraph(flow.getFlowGraph());
            if (intersects(fields, acceptedNames)) {
                references.put("decision_flow:" + flow.getFlowKey(), RuleReference.builder()
                        .type("decision_flow")
                        .id(flow.getId())
                        .name(flow.getFlowName())
                        .key(flow.getFlowKey())
                        .build());
            }
        }

        return references.values().stream()
                .sorted(Comparator.comparing(RuleReference::getType).thenComparing(RuleReference::getKey))
                .toList();
    }

    private FeatureDefinition getFeatureByCode(String code) {
        return featureDefinitionRepository.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("特征不存在: " + code));
    }

    private Resolution resolve(String fieldName) {
        String normalized = normalizeCode(fieldName);
        Optional<FeatureDefinition> direct = featureDefinitionRepository.findByCodeIgnoreCase(normalized);
        if (direct.isPresent()) {
            return new Resolution(direct.get(), null);
        }

        Optional<FeatureAlias> alias = featureAliasRepository.findByAliasCodeIgnoreCase(normalized);
        if (alias.isEmpty()) {
            return new Resolution(null, null);
        }
        FeatureDefinition definition = featureDefinitionRepository.findByCodeIgnoreCase(alias.get().getCanonicalCode())
                .orElse(null);
        return new Resolution(definition, alias.get());
    }

    private void validateAliasOwnership(String canonicalCode, Collection<String> aliases, String selfCode) {
        Set<String> normalizedAliases = normalizeAliases(aliases);
        for (String alias : normalizedAliases) {
            if (alias.equalsIgnoreCase(canonicalCode)) {
                throw new IllegalArgumentException("别名不能与特征编码相同: " + alias);
            }
            featureDefinitionRepository.findByCodeIgnoreCase(alias).ifPresent(existing -> {
                throw new IllegalArgumentException("别名与已有特征编码冲突: " + existing.getCode());
            });
            featureAliasRepository.findByAliasCodeIgnoreCase(alias).ifPresent(existing -> {
                if (selfCode == null || !existing.getCanonicalCode().equalsIgnoreCase(selfCode)) {
                    throw new IllegalArgumentException("别名已被其他特征占用: " + alias);
                }
            });
        }
    }

    private void replaceAliases(String canonicalCode, Collection<String> aliases) {
        featureAliasRepository.deleteByCanonicalCode(canonicalCode);
        List<FeatureAlias> newAliases = normalizeAliases(aliases).stream()
                .map(alias -> FeatureAlias.builder()
                        .aliasCode(alias)
                        .canonicalCode(canonicalCode)
                        .aliasType("LEGACY")
                        .status("ACTIVE")
                        .build())
                .toList();
        if (!newAliases.isEmpty()) {
            featureAliasRepository.saveAll(newAliases);
        }
    }

    private List<String> listAliases(String canonicalCode) {
        return featureAliasRepository.findByCanonicalCodeOrderByAliasCodeAsc(canonicalCode).stream()
                .map(FeatureAlias::getAliasCode)
                .toList();
    }

    private Map<String, List<String>> loadAliasesByCanonicalCode(List<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return featureAliasRepository.findByCanonicalCodeIn(codes).stream()
                .collect(Collectors.groupingBy(
                        FeatureAlias::getCanonicalCode,
                        Collectors.mapping(FeatureAlias::getAliasCode, Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted().toList()))
                ));
    }

    private FeatureDefinitionResponse toResponse(FeatureDefinition feature, List<String> aliases) {
        return FeatureDefinitionResponse.builder()
                .id(feature.getId())
                .code(feature.getCode())
                .name(feature.getName())
                .dataType(feature.getDataType())
                .sourceType(feature.getSourceType())
                .exampleValue(feature.getExampleValue())
                .description(feature.getDescription())
                .scope(feature.getScope())
                .sensitivity(feature.getSensitivity())
                .status(feature.getStatus())
                .owner(feature.getOwner())
                .createdAt(feature.getCreatedAt())
                .updatedAt(feature.getUpdatedAt())
                .aliases(new ArrayList<>(aliases))
                .build();
    }

    private List<String> checkOperatorCompatibility(String dataType, String operator, Object threshold) {
        if (operator == null || operator.isBlank() || dataType == null || dataType.isBlank()) {
            return List.of();
        }
        String normalizedType = normalizeOptionalEnum(dataType, dataType);
        String normalizedOperator = operator.trim().toUpperCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();

        if (Set.of("GT", "GE", "LT", "LE").contains(normalizedOperator)) {
            if (!NUMERIC_TYPES.contains(normalizedType)) {
                warnings.add(String.format(Locale.ROOT, "特征 %s 与运算符 %s 不匹配，应为数值类型", normalizedType, normalizedOperator));
            }
            if (threshold != null && !isNumeric(threshold)) {
                warnings.add("阈值类型不匹配，应为数值");
            }
        }

        if (Set.of("CONTAINS", "NOT_CONTAINS", "IN", "NOT_IN").contains(normalizedOperator)
                && !TEXT_TYPES.contains(normalizedType)) {
            warnings.add(String.format(Locale.ROOT, "特征 %s 与运算符 %s 不匹配，应为字符串或集合类型", normalizedType, normalizedOperator));
        }

        if (BOOLEAN_TYPES.contains(normalizedType) && threshold != null && !isBoolean(threshold)) {
            warnings.add("阈值类型不匹配，应为布尔值");
        }

        return warnings;
    }

    private Set<String> extractFieldsFromGroovyScript(String groovyScript) {
        Set<String> result = new LinkedHashSet<>();
        if (groovyScript == null || groovyScript.isBlank()) {
            return result;
        }
        Matcher matcher = FEATURE_PATTERN.matcher(groovyScript);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private Set<String> extractFieldsFromFlowGraph(String flowGraph) {
        Set<String> result = new LinkedHashSet<>();
        if (flowGraph == null || flowGraph.isBlank()) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(flowGraph);
            JsonNode nodes = root.path("nodes");
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    JsonNode data = node.path("data");
                    if (data.isMissingNode()) {
                        continue;
                    }
                    JsonNode fieldName = data.get("fieldName");
                    if (fieldName != null && !fieldName.asText().isBlank()) {
                        result.add(fieldName.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析决策流特征引用失败: {}", e.getMessage());
        }
        return result;
    }

    private boolean intersects(Set<String> left, Set<String> right) {
        for (String item : left) {
            if (right.stream().anyMatch(accepted -> accepted.equalsIgnoreCase(item))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizeAliases(Collection<String> aliases) {
        if (aliases == null) {
            return Set.of();
        }
        return aliases.stream()
                .map(this::trimToNull)
                .filter(value -> value != null)
                .map(this::normalizeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeCode(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException("特征编码不能为空");
        }
        return value;
    }

    private String normalizeFilter(String raw) {
        return trimToNull(raw);
    }

    private String normalizeEnumFilter(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredEnum(String raw, String message) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalEnum(String raw, String defaultValue) {
        String value = trimToNull(raw);
        return value == null ? defaultValue : value.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String stringValue) {
            return stringValue.matches("-?\\d+(\\.\\d+)?");
        }
        return false;
    }

    private boolean isBoolean(Object value) {
        if (value instanceof Boolean) {
            return true;
        }
        if (value instanceof String stringValue) {
            return "true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue);
        }
        return false;
    }

    private record Resolution(FeatureDefinition definition, FeatureAlias alias) {
    }
}
