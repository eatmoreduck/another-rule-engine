# Plan 02-03: 审计日志系统

**Wave:** 2 (核心功能)
**Estimated Time:** 4-5 hours
**Status:** Blocked by 02-01

## 目标

建立完整的审计日志系统，记录所有用户操作，支持操作追溯和合规审计。

## 需求映射

- **PERS-03**: 审计日志记录所有用户操作（谁、何时、做了什么）

## 前置依赖

- ✅ 02-01 完成（数据库基础设施可用）

## 任务列表

### Task 1: 审计日志表设计
**File:** `src/main/resources/db/migration/V3__create_audit_log.sql`

创建 `audit_logs` 表：
- `id` BIGINT PRIMARY KEY
- `entity_type` VARCHAR(100) NOT NULL（实体类型：RULE, VERSION, CONFIG等）
- `entity_id` VARCHAR(255) NOT NULL（实体ID）
- `operation` VARCHAR(50) NOT NULL（操作类型：CREATE, UPDATE, DELETE, ROLLBACK, VIEW等）
- `operation_detail` TEXT（操作详情JSON）
- `operator` VARCHAR(255) NOT NULL（操作人）
- `operator_ip` VARCHAR(50)（操作人IP）
- `operation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP（操作时间）
- `status` VARCHAR(50) NOT NULL（SUCCESS, FAILED）
- `error_message` TEXT（错误信息）
- `request_id` VARCHAR(100)（请求ID，用于关联多个操作）
- INDEX on `entity_type`, `entity_id`, `operation`, `operation_time`
- 分区策略（可选）：按 `operation_time` 月度分区

### Task 2: AuditLog Entity
**File:** `src/main/java/com/example/ruleengine/domain/AuditLog.java`

创建 AuditLog 实体类：
- JPA 映射所有字段
- 使用 `@PrePersist` 自动设置操作时间
- 添加 `operation_detail` 的 JSON 序列化/反序列化支持

### Task 3: AuditLogRepository
**File:** `src/main/java/com/example/ruleengine/repository/AuditLogRepository.java`

创建 Repository 并添加查询方法：
- `findByEntityTypeAndEntityIdOrderByOperationTimeDesc(String entityType, String entityId)`
- `findByOperatorOrderByOperationTimeDesc(String operator)`
- `findByOperationTimeBetween(LocalDateTime start, LocalDateTime end)`
- `findByRequestId(String requestId)` - 关联同一请求的所有操作

### Task 4: AuditEvent 枚举和常量
**File:** `src/main/java/com/example/ruleengine/constants/AuditEvent.java`

定义审计事件类型：
```java
public enum AuditEvent {
    // 规则操作
    RULE_CREATE("规则创建"),
    RULE_UPDATE("规则更新"),
    RULE_DELETE("规则删除"),
    RULE_ENABLE("规则启用"),
    RULE_DISABLE("规则禁用"),

    // 版本操作
    VERSION_CREATE("版本创建"),
    VERSION_ROLLBACK("版本回滚"),
    VERSION_VIEW("版本查看"),

    // 配置操作
    CONFIG_UPDATE("配置更新"),

    // 系统操作
    SYSTEM_LOGIN("系统登录"),
    SYSTEM_LOGOUT("系统登出");

    private final String description;
}
```

### Task 5: AuditLogService
**File:** `src/main/java/com/example/ruleengine/service/AuditLogService.java`

实现审计日志服务：

**方法 1: logOperation()**
```java
/**
 * 记录操作日志
 */
void logOperation(String entityType, String entityId,
                 AuditEvent operation, String operator,
                 Map<String, Object> details);
```

**方法 2: logOperationWithContext()**
```java
/**
 * 记录操作日志（带上下文信息：IP、Request ID等）
 */
void logOperationWithContext(String entityType, String entityId,
                            AuditEvent operation, String operator,
                            Map<String, Object> details,
                            String operatorIp, String requestId);
```

**方法 3: getAuditHistory()**
```java
/**
 * 获取实体审计历史
 */
List<AuditLog> getAuditHistory(String entityType, String entityId);
```

**方法 4: getOperatorActivity()**
```java
/**
 * 获取操作人活动记录
 */
List<AuditLog> getOperatorActivity(String operator,
                                  LocalDateTime start, LocalDateTime end);
```

### Task 6: AOP 审计切面
**File:** `src/main/java/com/example/ruleengine/aspect/AuditLogAspect.java`

使用 Spring AOP 自动记录审计日志：
```java
@Aspect
@Component
public class AuditLogAspect {
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint,
                             Auditable auditable) throws Throwable {
        // 1. 提取方法参数
        // 2. 调用方法
        // 3. 记录审计日志
        // 4. 返回结果
    }
}
```

### Task 7: @Auditable 注解
**File:** `src/main/java/com/example/ruleengine/annotation/Auditable.java`

创建自定义注解：
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    AuditEvent event();
    String entityType();
    String entityIdExpression() default ""; // SpEL 表达式提取 ID
}
```

### Task 8: 应用到现有服务
**Files:**
- `VersionManagementService.java`
- `RuleExecutionService.java` (后续)
- 其他需要审计的服务

添加 `@Auditable` 注解：
```java
@Service
public class VersionManagementService {
    @Auditable(event = AuditEvent.VERSION_CREATE,
               entityType = "RULE",
               entityIdExpression = "#ruleKey")
    public RuleVersion createVersion(String ruleKey, ...) {
        // 方法实现
    }
}
```

### Task 9: AuditLogController
**File:** `src/main/java/com/example/ruleengine/controller/AuditLogController.java`

创建审计日志查询 API：
- `GET /api/v1/audit/logs/{entityType}/{entityId}` - 获取实体审计历史
- `GET /api/v1/audit/logs/operator/{operator}` - 获取操作人活动
- `GET /api/v1/audit/logs/time-range` - 按时间范围查询
- `GET /api/v1/audit/logs/request/{requestId}` - 按 Request ID 查询

### Task 10: 单元测试
**File:** `src/test/java/com/example/ruleengine/service/AuditLogServiceTest.java`

测试覆盖：
- 记录操作日志成功
- 查询审计历史
- AOP 切面自动记录
- 操作详情 JSON 序列化

### Task 11: 集成测试
**File:** `src/test/java/com/example/ruleengine/controller/AuditLogControllerTest.java`

测试完整的审计流程。

## 验证策略

### 手动验证
1. 调用带 `@Auditable` 注解的方法
2. 查询审计日志 API，验证记录正确
3. 检查数据库 `audit_logs` 表
4. 验证操作详情 JSON 格式正确

### 自动化测试
```bash
# 运行审计日志测试
./gradlew test --tests AuditLogServiceTest
./gradlew test --tests AuditLogControllerTest

# 运行集成测试
./gradlew integrationTest
```

### 性能测试
- 审计日志写入性能（异步写入）
- 大量日志查询性能

## 成功标准

- [ ] `audit_logs` 表创建成功
- [ ] AuditLog Entity 和 Repository 创建完成
- [ ] AuditLogService 实现所有方法
- [ ] AOP 切面和 `@Auditable` 注解工作正常
- [ ] 所有用户操作自动记录审计日志
- [ ] 审计日志查询 API 工作正常
- [ ] 所有单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试通过（写入 < 10ms）

## 输出产物

- `V3__create_audit_log.sql` - 数据库迁移脚本
- `AuditLog.java` - JPA 实体类
- `AuditLogRepository.java` - 数据访问接口
- `AuditEvent.java` - 审计事件枚举
- `AuditLogService.java` - 审计日志服务
- `AuditLogAspect.java` - AOP 切面
- `@Auditable.java` - 自定义注解
- `AuditLogController.java` - REST API 控制器
- 单元测试和集成测试

## 关键设计决策

### 异步写入 vs 同步写入
- **选择**: 异步写入（使用 Spring `@Async` 或消息队列）
- **理由**:
  - 审计日志不应阻塞主业务流程
  - 即使日志写入失败，也不应影响规则执行
- **实现**: 使用 Spring `@Async` + 线程池，失败时记录到本地日志

### 日志保留策略
- **热数据**: 最近 3 个月数据保存在主表
- **温数据**: 3-12 个月数据归档到归档表
- **冷数据**: 12 个月以上数据导出到对象存储或删除
- **实现**: 定时任务（Spring `@Scheduled`）自动归档

### 敏感信息处理
- **不记录**: 密码、Token、敏感个人信息
- **脱敏**: 手机号、身份证号部分隐藏
- **实现**: 在 `operation_detail` JSON 序列化时脱敏

### 性能优化
1. **批量写入**: 使用 JDBC batch 插入
2. **索引优化**: 合理创建索引，避免过多
3. **分区表**: 按时间分区，提升查询和清理性能
4. **缓存**: 不缓存审计日志（数据量大，查询频率低）

## 风险与注意事项

1. **性能影响**: 审计日志写入可能影响主业务性能，使用异步解决
2. **数据量过大**: 需要设计归档和清理策略
3. **信息泄露**: 审计日志可能包含敏感信息，需要脱敏
4. **日志丢失**: 异步写入可能丢失日志，需要监控和告警

## 后续依赖

- ✅ 完成后可开始 **02-04: 规则生命周期管理**
- ✅ 为 **Phase 4: 监控与安全增强** 提供审计基础

## 合规性考虑

### GDPR 合规
- 提供用户数据删除功能（"被遗忘权"）
- 审计日志保留期限不超过业务需求

### 等保 2.0 要求
- 记录所有用户操作
- 审计日志保留至少 6 个月
- 审计日志应防止篡改

## 相关文档

- Spring AOP: https://spring.io/guides/gs/aspectj-annotation/
- GDPR: https://gdpr-info.eu/
- 等保 2.0: https://www.tc260.org.cn/
