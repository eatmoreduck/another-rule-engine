# Plan 02-04: 规则生命周期管理

**Wave:** 3 (业务功能)
**Estimated Time:** 5-6 hours
**Status:** Blocked by 02-01, 02-02, 02-03

## 目标

实现规则的完整生命周期管理，包括创建、编辑、启用/禁用、删除等功能，并集成版本管理和审计日志。

## 需求映射

- **PERS-01**: 规则元数据持久化到 PostgreSQL（使用 JPA）
- **VER-01**: 系统支持规则多版本管理，可创建、修改、删除版本
- **PERS-03**: 审计日志记录所有用户操作

## 前置依赖

- ✅ 02-01 完成（数据库基础设施）
- ✅ 02-02 完成（版本管理）
- ✅ 02-03 完成（审计日志）

## 任务列表

### Task 1: RuleLifecycleService
**File:** `src/main/java/com/example/ruleengine/service/RuleLifecycleService.java`

实现规则生命周期管理服务：

**方法 1: createRule()**
```java
/**
 * 创建新规则
 * 1. 验证 rule_key 唯一性
 * 2. 验证 Groovy 脚本语法
 * 3. 创建规则实体（version=1, status=DRAFT）
 * 4. 记录审计日志
 * 5. 返回创建的规则
 */
@Auditable(event = AuditEvent.RULE_CREATE,
           entityType = "RULE",
           entityIdExpression = "#request.ruleKey")
Rule createRule(CreateRuleRequest request, String operator);
```

**方法 2: updateRule()**
```java
/**
 * 更新规则（自动创建新版本）
 * 1. 验证规则存在
 * 2. 验证 Groovy 脚本语法
 * 3. 调用 VersionManagementService 创建新版本
 * 4. 更新规则实体
 * 5. 记录审计日志
 * 6. 返回更新后的规则
 */
@Auditable(event = AuditEvent.RULE_UPDATE,
           entityType = "RULE",
           entityIdExpression = "#ruleKey")
Rule updateRule(String ruleKey, UpdateRuleRequest request, String operator);
```

**方法 3: deleteRule()**
```java
/**
 * 删除规则（软删除）
 * 1. 验证规则存在
 * 2. 检查规则是否在使用中
 * 3. 设置 status=DELETED
 * 4. 记录审计日志
 */
@Auditable(event = AuditEvent.RULE_DELETE,
           entityType = "RULE",
           entityIdExpression = "#ruleKey")
void deleteRule(String ruleKey, String operator);
```

**方法 4: enableRule()**
```java
/**
 * 启用规则
 */
@Auditable(event = AuditEvent.RULE_ENABLE,
           entityType = "RULE",
           entityIdExpression = "#ruleKey")
Rule enableRule(String ruleKey, String operator);
```

**方法 5: disableRule()**
```java
/**
 * 禁用规则
 */
@Auditable(event = AuditEvent.RULE_DISABLE,
           entityType = "RULE",
           entityIdExpression = "#ruleKey")
Rule disableRule(String ruleKey, String operator);
```

**方法 6: getRule()**
```java
/**
 * 获取规则详情
 */
Rule getRule(String ruleKey);
```

**方法 7: listRules()**
```java
/**
 * 列出所有规则（支持分页和过滤）
 */
Page<Rule> listRules(RuleQuery query);
```

### Task 2: Groovy 脚本验证器
**File:** `src/main/java/com/example/ruleengine/validator/GroovyScriptValidator.java`

实现 Groovy 脚本语法验证：
```java
@Component
public class GroovyScriptValidator {
    /**
     * 验证 Groovy 脚本语法
     * 1. 尝试编译脚本
     * 2. 检查是否有语法错误
     * 3. 返回验证结果和错误信息
     */
    public ValidationResult validate(String script) {
        try {
            // 尝试编译脚本
            new GroovyShell().parse(script);
            return ValidationResult.success();
        } catch (CompilationFailedException e) {
            return ValidationResult.error(e.getMessage());
        }
    }
}
```

### Task 3: RuleStatus 枚举
**File:** `src/main/java/com/example/ruleengine/constants/RuleStatus.java`

定义规则状态：
```java
public enum RuleStatus {
    DRAFT("草稿"),
    ACTIVE("生效中"),
    ARCHIVED("已归档"),
    DELETED("已删除");

    private final String description;
}
```

### Task 4: RuleQuery 查询对象
**File:** `src/main/java/com/example/ruleengine/model/dto/RuleQuery.java`

创建规则查询对象：
- 支持按状态过滤
- 支持按创建人过滤
- 支持按时间范围过滤
- 支持关键词搜索（rule_key, rule_name）
- 支持分页（Pageable）

### Task 5: RuleController
**File:** `src/main/java/com/example/ruleengine/controller/RuleController.java`

创建规则管理 REST API：
- `POST /api/v1/rules` - 创建规则
- `PUT /api/v1/rules/{ruleKey}` - 更新规则
- `DELETE /api/v1/rules/{ruleKey}` - 删除规则
- `POST /api/v1/rules/{ruleKey}/enable` - 启用规则
- `POST /api/v1/rules/{ruleKey}/disable` - 禁用规则
- `GET /api/v1/rules/{ruleKey}` - 获取规则详情
- `GET /api/v1/rules` - 列出所有规则（分页）
- `POST /api/v1/rules/validate` - 验证 Groovy 脚本

### Task 6: DTO 类
**File:** `src/main/java/com/example/ruleengine/model/dto/`

创建数据传输对象：
- `CreateRuleRequest.java` - 创建规则请求
  - `ruleKey`, `ruleName`, `ruleDescription`, `groovyScript`
- `UpdateRuleRequest.java` - 更新规则请求
  - `ruleName`, `ruleDescription`, `groovyScript`
- `RuleResponse.java` - 规则响应
- `ValidateScriptRequest.java` - 脚本验证请求
- `ValidateScriptResponse.java` - 脚本验证响应

### Task 7: 规则使用中检查
**File:** `src/main/java/com/example/ruleengine/service/RuleUsageChecker.java`

实现规则使用检查：
```java
@Component
public class RuleUsageChecker {
    /**
     * 检查规则是否在使用中
     * TODO: 在 Phase 5 实现灰度发布后，检查是否有灰度配置
     * TODO: 在 Phase 6 实现依赖分析后，检查是否有其他规则依赖
     */
    public boolean isRuleInUse(String ruleKey) {
        // 后续实现
        return false;
    }
}
```

### Task 8: 集成 RuleExecutionService
**File:** `src/main/java/com/example/ruleengine/service/RuleExecutionService.java`

修改规则执行服务，从数据库加载规则：
```java
@Service
public class RuleExecutionService {
    @Autowired
    private RuleRepository ruleRepository;

    public DecisionResponse decide(DecisionRequest request) {
        // 1. 从数据库加载规则（根据 rule_key）
        Rule rule = ruleRepository.findByRuleKeyAndEnabledTrue(request.getRuleKey());

        // 2. 检查规则状态
        if (rule == null || rule.getStatus() != RuleStatus.ACTIVE) {
            throw new RuleNotFoundException("规则不存在或未启用");
        }

        // 3. 获取 Groovy 脚本
        String script = rule.getGroovyScript();

        // 4. 执行脚本（现有逻辑）
        // ...
    }
}
```

### Task 9: 单元测试
**File:** `src/test/java/com/example/ruleengine/service/RuleLifecycleServiceTest.java`

测试覆盖：
- 创建规则成功，版本号为 1
- 更新规则自动创建新版本
- 删除规则（软删除）成功
- 启用/禁用规则
- 查询规则列表（分页、过滤）
- Groovy 脚本验证

### Task 10: 集成测试
**File:** `src/test/java/com/example/ruleengine/controller/RuleControllerTest.java`

测试完整的规则生命周期：
1. 创建规则 → 状态为 DRAFT
2. 启用规则 → 状态为 ACTIVE
3. 更新规则 → 创建版本 2
4. 禁用规则 → enabled=false
5. 删除规则 → status=DELETED

### Task 11: 端到端测试
**File:** `src/test/java/com/example/ruleengine/integration/RuleLifecycleE2ETest.java`

测试完整的流程：
1. 创建规则
2. 启用规则
3. 通过决策 API 执行规则
4. 验证规则执行结果正确

## 验证策略

### 手动验证
1. 使用 Postman 或 curl 测试所有 API
2. 验证规则状态转换正确
3. 验证版本号自动递增
4. 验证审计日志正确记录
5. 验证决策 API 能正确加载并执行规则

### 自动化测试
```bash
# 运行单元测试
./gradlew test --tests RuleLifecycleServiceTest

# 运行集成测试
./gradlew test --tests RuleControllerTest

# 运行端到端测试
./gradlew test --tests RuleLifecycleE2ETest
```

## 成功标准

- [ ] RuleLifecycleService 实现所有方法
- [ ] Groovy 脚本验证器工作正常
- [ ] RuleController 提供完整 API
- [ ] 规则状态转换正确（DRAFT → ACTIVE → ARCHIVED/DELETED）
- [ ] 规则更新自动创建新版本
- [ ] 所有操作记录审计日志
- [ ] 规则执行服务能从数据库加载规则
- [ ] 所有单元测试通过（覆盖率 > 80%）
- [ ] 集成测试通过
- [ ] 端到端测试通过

## 输出产物

- `RuleLifecycleService.java` - 规则生命周期管理服务
- `GroovyScriptValidator.java` - Groovy 脚本验证器
- `RuleStatus.java` - 规则状态枚举
- `RuleQuery.java` - 规则查询对象
- `RuleController.java` - REST API 控制器
- DTO 类（CreateRuleRequest, UpdateRuleRequest, RuleResponse, ValidateScriptRequest/Response）
- `RuleUsageChecker.java` - 规则使用检查器
- `RuleExecutionService.java` (修改) - 集成数据库加载
- 单元测试、集成测试、端到端测试

## 关键设计决策

### 规则删除策略
- **软删除**: 设置 `status=DELETED`，不物理删除
- **理由**:
  - 保留历史记录用于审计
  - 可恢复误删除的规则
  - 避免破坏关联关系（版本历史、审计日志）

### 规则唯一性
- **rule_key 唯一**: 业务层面确保规则唯一标识
- **数据库约束**: 添加 `UNIQUE` 约束
- **创建前检查**: 检查 `rule_key` 是否已存在

### Groovy 脚本验证时机
- **创建/更新时验证**: 在保存前验证语法
- **执行时不验证**: 避免影响性能
- **编译缓存**: 验证通过的脚本缓存编译结果

### 规则状态转换
```
创建 → DRAFT
DRAFT → ACTIVE（启用）
ACTIVE → DRAFT（禁用，但保留编辑）
DRAFT → ARCHIVED（归档）
任何状态 → DELETED（删除）
DELETED → DRAFT（恢复，可选功能）
```

## 风险与注意事项

1. **并发修改**: 多人同时修改同一规则，需要乐观锁或悲观锁
2. **规则依赖**: 删除规则可能影响依赖该规则的其他规则（Phase 6 处理）
3. **性能影响**: 从数据库加载规则可能影响性能，需要缓存（Phase 4 处理）
4. **脚本安全**: Groovy 脚本可能包含恶意代码，需要沙箱（Phase 1 已部分实现，Phase 4 增强）

## 后续依赖

- ✅ 完成后可开始 **02-05: 性能优化与缓存**
- ✅ 为 **Phase 3: 规则配置界面** 提供后端 API
- ✅ 为 **Phase 5: 灰度发布** 提供规则基础

## 相关文档

- JPA Entity Lifecycle: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- Soft Delete Pattern: https://www.baeldung.com/jpa-soft-delete
