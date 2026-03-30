package com.example.ruleengine.service.decisiontable;

import com.example.ruleengine.model.dto.DecisionTableRequest;
import com.example.ruleengine.model.dto.DecisionTableResponse;
import com.example.ruleengine.model.dto.DecisionTableValidateResponse;
import com.example.ruleengine.validator.GroovyScriptValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * 决策表服务
 * 支持多维度条件组合（类似 Excel 决策表）
 * 输入: 行列表格式的条件+动作
 * 输出: 自动生成 Groovy DSL 脚本
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionTableService {

    private final GroovyScriptValidator scriptValidator;

    /**
     * 将决策表转换为 Groovy DSL 脚本
     */
    public DecisionTableResponse convertToGroovy(DecisionTableRequest request) {
        log.info("转换决策表为Groovy脚本, 规则名: {}", request.getRuleName());

        // 1. 验证决策表
        DecisionTableValidateResponse validation = validate(request);
        if (!validation.isValid()) {
            return DecisionTableResponse.error(String.join("; ", validation.getErrors()));
        }

        // 2. 生成 Groovy 脚本
        String groovyScript = generateGroovyScript(request);

        // 3. 验证生成的脚本语法
        GroovyScriptValidator.ValidationResult scriptValidation = scriptValidator.validate(groovyScript);
        if (!scriptValidation.isValid()) {
            return DecisionTableResponse.error("生成的脚本语法错误: " + scriptValidation.getErrorMessage());
        }

        return DecisionTableResponse.success(groovyScript, request.getRows().size());
    }

    /**
     * 验证决策表
     */
    public DecisionTableValidateResponse validate(DecisionTableRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 验证条件列
        if (request.getConditionColumns() == null || request.getConditionColumns().isEmpty()) {
            errors.add("条件列不能为空");
        }

        // 验证动作列
        if (request.getActionColumns() == null || request.getActionColumns().isEmpty()) {
            errors.add("动作列不能为空");
        }

        if (errors.isEmpty()) {
            // 验证每行数据
            for (int i = 0; i < request.getRows().size(); i++) {
                DecisionTableRequest.DecisionRow row = request.getRows().get(i);
                int rowNum = i + 1;

                // 检查条件值是否覆盖所有条件列
                if (row.getConditions() != null) {
                    for (String condCol : request.getConditionColumns().keySet()) {
                        if (!row.getConditions().containsKey(condCol)) {
                            errors.add(String.format("第 %d 行缺少条件列 '%s' 的值", rowNum, condCol));
                        }
                    }
                }

                // 检查动作值是否覆盖所有动作列
                if (row.getActions() != null) {
                    for (String actCol : request.getActionColumns().keySet()) {
                        if (!row.getActions().containsKey(actCol)) {
                            errors.add(String.format("第 %d 行缺少动作列 '%s' 的值", rowNum, actCol));
                        }
                    }
                }
            }

            // 检查是否有重复行
            checkDuplicateRows(request, warnings);
        }

        if (!errors.isEmpty()) {
            return DecisionTableValidateResponse.invalid(errors);
        }

        if (!warnings.isEmpty()) {
            return DecisionTableValidateResponse.withWarnings(warnings);
        }

        return DecisionTableValidateResponse.valid();
    }

    /**
     * 生成 Groovy DSL 脚本
     */
    private String generateGroovyScript(DecisionTableRequest request) {
        String ruleName = request.getRuleName() != null ? request.getRuleName() : "DecisionTableRule";

        StringBuilder script = new StringBuilder();
        script.append("def evaluate(context) {\n");

        // 收集所有条件列名和动作列名
        List<String> conditionNames = new ArrayList<>(request.getConditionColumns().keySet());
        List<String> actionNames = new ArrayList<>(request.getActionColumns().keySet());

        // 按条件列分组: 先按非通配符条件数量降序排列（更精确的条件优先匹配）
        List<DecisionTableRequest.DecisionRow> sortedRows = request.getRows().stream()
                .sorted((a, b) -> {
                    long aSpecificity = a.getConditions().values().stream()
                            .filter(v -> !"*".equals(String.valueOf(v))).count();
                    long bSpecificity = b.getConditions().values().stream()
                            .filter(v -> !"*".equals(String.valueOf(v))).count();
                    return Long.compare(bSpecificity, aSpecificity);
                })
                .collect(Collectors.toList());

        // 生成条件判断
        for (int i = 0; i < sortedRows.size(); i++) {
            DecisionTableRequest.DecisionRow row = sortedRows.get(i);

            if (i == 0) {
                script.append("  ");
            } else {
                script.append("  } else ");
            }

            // 构建条件表达式
            String conditionExpr = buildConditionExpression(row.getConditions(), conditionNames, request.getConditionColumns());
            script.append("if (").append(conditionExpr).append(") {\n");

            // 构建动作返回
            String actionExpr = buildActionExpression(row.getActions(), actionNames);
            script.append("    return ").append(actionExpr).append("\n");
        }

        // 默认返回（无匹配时）
        script.append("  }\n");
        script.append("  return [hit: false, action: 'PASS', reason: '未匹配任何决策规则']\n");
        script.append("}\n");

        return script.toString();
    }

    /**
     * 构建条件表达式
     */
    private String buildConditionExpression(Map<String, Object> conditions, List<String> conditionNames,
                                            Map<String, String> conditionTypes) {
        StringJoiner joiner = new StringJoiner(" && ");

        for (String condName : conditionNames) {
            Object value = conditions.get(condName);
            if (value == null || "*".equals(String.valueOf(value))) {
                continue; // 通配符，跳过此条件
            }

            String type = conditionTypes.getOrDefault(condName, "STRING");
            String condition;

            switch (type.toUpperCase()) {
                case "NUMBER":
                    condition = String.format("(context.%s as BigDecimal) == (%s as BigDecimal)", condName, value);
                    break;
                case "BOOLEAN":
                    condition = String.format("context.%s == %s", condName, value);
                    break;
                case "STRING":
                default:
                    condition = String.format("context.%s == '%s'", condName, escapeGroovy(String.valueOf(value)));
                    break;
            }

            joiner.add(condition);
        }

        String result = joiner.toString();
        return result.isEmpty() ? "true" : result;
    }

    /**
     * 构建动作返回表达式
     */
    private String buildActionExpression(Map<String, Object> actions, List<String> actionNames) {
        StringBuilder sb = new StringBuilder("[hit: true");

        for (String actName : actionNames) {
            Object value = actions.get(actName);
            if (value != null) {
                sb.append(", ").append(actName).append(": '").append(escapeGroovy(String.valueOf(value))).append("'");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 检查重复行
     */
    private void checkDuplicateRows(DecisionTableRequest request, List<String> warnings) {
        for (int i = 0; i < request.getRows().size(); i++) {
            for (int j = i + 1; j < request.getRows().size(); j++) {
                DecisionTableRequest.DecisionRow row1 = request.getRows().get(i);
                DecisionTableRequest.DecisionRow row2 = request.getRows().get(j);

                if (row1.getConditions().equals(row2.getConditions())) {
                    warnings.add(String.format("第 %d 行和第 %d 行的条件完全相同", i + 1, j + 1));
                }
            }
        }
    }

    /**
     * 转义 Groovy 字符串中的特殊字符
     */
    private String escapeGroovy(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
