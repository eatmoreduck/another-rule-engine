# Plan 02-02: 规则版本管理

**Wave:** 2 (核心功能)
**Estimated Time:** 5-6 hours
**Status:** Blocked by 02-01

## 目标

实现规则的多版本管理功能，支持版本创建、查询、比较和回滚。

## 需求映射

- **VER-01**: 系统支持规则多版本管理，可创建、修改、删除版本
- **VER-02**: 用户可一键回滚规则到任意历史版本
- **PERS-02**: 规则变更历史完整记录，支持追溯

## 前置依赖

- ✅ 02-01 完成（数据库基础设施可用）

## 任务列表

### Task 1: 版本历史表设计
**File:** `src/main/resources/db/migration/V2__create_version_history.sql`

创建 `rule_versions` 表：
- `id` BIGINT PRIMARY KEY
- `rule_id` BIGINT NOT NULL（关联 rules.id）
- `rule_key` VARCHAR(255) NOT NULL（规则标识，冗余字段便于查询）
- `version` INT NOT NULL（版本号）
- `groovy_script` TEXT NOT NULL（该版本的 Groovy 脚本）
- `change_reason` TEXT（变更原因）
- `changed_by` VARCHAR(255) NOT NULL（变更人）
- `changed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP（变更时间）
- `is_rollback` BOOLEAN DEFAULT FALSE（是否为回滚操作）
- `rollback_from_version` INT（回滚来源版本）
- FOREIGN KEY on `rule_id`
- INDEX on `rule_id`, `rule_key`, `version`
- UNIQUE constraint on `(rule_id, version)`

### Task 2: RuleVersion Entity
**File:** `src/main/java/com/example/ruleengine/domain/RuleVersion.java`

创建 RuleVersion 实体类：
- JPA 映射所有字段
- 添加 `@ManyToOne` 关联到 Rule
- 使用 Lombok 简化代码

### Task 3: RuleVersionRepository
**File:** `src/main/java/com/example/ruleengine/repository/RuleVersionRepository.java`

创建 Repository 并添加查询方法：
- `findByRuleIdOrderByVersionDesc(Long ruleId)` - 查询规则所有版本
- `findByRuleIdAndVersion(Long ruleId, Integer version)` - 查询特定版本
- `findFirstByRuleIdOrderByVersionDesc(Long ruleId)` - 获取最新版本
- `findByRuleKeyOrderByVersionDesc(String ruleKey)` - 按 rule_key 查询

### Task 4: VersionManagementService
**File:** `src/main/java/com/example/ruleengine/service/VersionManagementService.java`

实现版本管理服务：

**方法 1: createVersion()**
```java
/**
 * 创建规则新版本
 * 1. 保存当前版本到 rule_versions
 * 2. 更新 rules 表的 groovy_script 和 version
 * 3. 记录变更原因和操作人
 */
RuleVersion createVersion(String ruleKey, String newScript,
                         String changeReason, String operator);
```

**方法 2: getVersions()**
```java
/**
 * 获取规则的所有历史版本
 */
List<RuleVersion> getVersions(String ruleKey);
```

**方法 3: getVersion()**
```java
/**
 * 获取规则的特定版本
 */
RuleVersion getVersion(String ruleKey, Integer version);
```

**方法 4: compareVersions()**
```java
/**
 * 比较两个版本的差异
 * 返回差异的 Groovy 脚本内容
 */
VersionDiff compareVersions(String ruleKey, Integer version1, Integer version2);
```

**方法 5: rollbackToVersion()**
```java
/**
 * 回滚规则到指定版本
 * 1. 将当前版本保存到 rule_versions（标记为 is_rollback=true）
 * 2. 从 rule_versions 恢复目标版本的脚本
 * 3. 更新 rules 表
 * 4. 记录回滚操作
 */
Rule rollbackToVersion(String ruleKey, Integer targetVersion, String operator);
```

### Task 5: RuleVersionController
**File:** `src/main/java/com/example/ruleengine/controller/RuleVersionController.java`

创建 REST API：

- `GET /api/v1/rules/{ruleKey}/versions` - 获取所有版本
- `GET /api/v1/rules/{ruleKey}/versions/{version}` - 获取特定版本
- `POST /api/v1/rules/{ruleKey}/versions` - 创建新版本
- `POST /api/v1/rules/{ruleKey}/versions/{version}/rollback` - 回滚到指定版本
- `GET /api/v1/rules/{ruleKey}/versions/compare` - 比较两个版本

### Task 6: DTO 类
**File:** `src/main/java/com/example/ruleengine/model/dto/`

创建数据传输对象：
- `CreateVersionRequest.java` - 创建版本请求
- `RollbackRequest.java` - 回滚请求
- `VersionResponse.java` - 版本响应
- `VersionDiffResponse.java` - 版本差异响应

### Task 7: 单元测试
**File:** `src/test/java/com/example/ruleengine/service/VersionManagementServiceTest.java`

测试覆盖：
- 创建版本成功，版本号递增
- 回滚到历史版本成功
- 回滚后再创建版本，版本号继续递增
- 比较版本差异
- 获取版本列表

## 验证策略

### 手动验证
1. 使用 Postman 或 curl 测试所有 API
2. 验证版本号递增正确
3. 验证回滚后脚本内容正确
4. 检查数据库 rule_versions 表数据

### 自动化测试
```bash
# 运行服务测试
./gradlew test --tests VersionManagementServiceTest

# 运行集成测试
./gradlew test --tests RuleVersionControllerTest
```

### 集成测试场景
1. 创建规则 → 创建版本 1
2. 修改规则 → 创建版本 2
3. 再次修改 → 创建版本 3
4. 回滚到版本 1 → 创建版本 4（标记为回滚）
5. 验证 rules 表当前为版本 1 的内容
6. 验证 rule_versions 有 4 条记录

## 成功标准

- [ ] `rule_versions` 表创建成功
- [ ] RuleVersion Entity 和 Repository 创建完成
- [ ] VersionManagementService 实现所有方法
- [ ] RuleVersionController 提供完整 API
- [ ] 版本创建后版本号正确递增
- [ ] 回滚功能正常，脚本内容正确恢复
- [ ] 版本比较功能正常
- [ ] 所有单元测试通过（覆盖率 > 80%）
- [ ] 集成测试通过

## 输出产物

- `V2__create_version_history.sql` - 数据库迁移脚本
- `RuleVersion.java` - JPA 实体类
- `RuleVersionRepository.java` - 数据访问接口
- `VersionManagementService.java` - 版本管理服务
- `RuleVersionController.java` - REST API 控制器
- DTO 类（CreateVersionRequest, RollbackRequest, VersionResponse, VersionDiffResponse）
- `VersionManagementServiceTest.java` - 单元测试
- `RuleVersionControllerTest.java` - 集成测试

## 关键设计决策

### 版本号策略
- **递增策略**: 每次创建新版本，版本号 +1
- **回滚策略**: 回滚不算"新版本"，而是创建一个指向历史版本的新版本
  - 例如: v1 → v2 → v3 → 回滚到 v1 → 创建 v4（内容与 v1 相同）
- **好处**: 保留完整历史，可追溯所有操作

### 性能考虑
- `rule_versions` 表会快速增长，考虑：
  1. 定期归档旧版本数据
  2. 添加分区表（按时间）
  3. 只保留最近 N 个版本（配置化）

### 数据一致性
- 使用 `@Transactional` 确保版本创建和规则更新的原子性
- 考虑添加乐观锁（`@Version`）防止并发修改冲突

## 风险与注意事项

1. **版本冲突**: 多人同时修改同一规则，需要处理并发冲突
2. **版本数量过多**: 需要设计版本清理策略
3. **回滚风险**: 回滚后可能影响正在运行的决策，需要灰度机制（Phase 5）
4. **脚本差异比较**: Groovy 脚本差异比较可能复杂，可考虑使用 Apache Commons Text 的 `StringSubstitutor`

## 后续依赖

- ✅ 完成后可开始 **02-03: 审计日志系统**
- ✅ 为 **Phase 5: 灰度发布** 提供版本基础

## 相关文档

- Semantic Versioning: https://semver.org/
- Spring Data JPA - Entity Graph: https://spring.io/projects/spring-data-jpa
