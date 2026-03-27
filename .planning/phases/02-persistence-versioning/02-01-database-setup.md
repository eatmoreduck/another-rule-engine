# Plan 02-01: 数据库基础设施搭建

**Wave:** 1 (基础设施)
**Estimated Time:** 3-4 hours
**Status:** Ready to start

## 目标

搭建 PostgreSQL 数据库基础设施，配置 JPA 持久化层，为规则持久化和版本管理奠定基础。

## 需求映射

- **PERS-01**: 规则元数据持久化到 PostgreSQL（使用 JPA）

## 前置依赖

- Phase 1 完成（规则执行引擎已可用）
- PostgreSQL 数据库可访问（192.168.5.202:5432）

## 任务列表

### Task 1: 数据库初始化
**File:** `src/main/resources/db/migration/V1__init_database.sql`

创建数据库和基础表结构：
- 创建 `yare_engine` 数据库（如果不存在）
- 创建 `rules` 表（规则主表）
  - `id` BIGINT PRIMARY KEY
  - `rule_key` VARCHAR(255) UNIQUE NOT NULL（规则唯一标识）
  - `rule_name` VARCHAR(255) NOT NULL（规则名称）
  - `rule_description` TEXT（规则描述）
  - `groovy_script` TEXT NOT NULL（Groovy DSL 脚本）
  - `version` INT NOT NULL DEFAULT 1（当前版本号）
  - `status` VARCHAR(50) NOT NULL（DRAFT/ACTIVE/ARCHIVED）
  - `created_by` VARCHAR(255) NOT NULL（创建人）
  - `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  - `updated_by` VARCHAR(255)（更新人）
  - `updated_at` TIMESTAMP
  - `enabled` BOOLEAN NOT NULL DEFAULT TRUE（是否启用）
  - INDEX on `rule_key`, `status`, `enabled`

### Task 2: Gradle 依赖配置
**File:** `build.gradle`

添加必要的依赖：
- `spring-boot-starter-data-jpa`
- `postgresql` (42.6.0+)
- `flyway` (数据库迁移)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'org.postgresql:postgresql:42.7.2'
implementation 'org.flywaydb:flyway-core:10.10.0'
```

### Task 3: 数据库连接配置
**File:** `src/main/resources/application.yml`

配置 PostgreSQL 连接：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://192.168.5.202:5432/yare_engine
    username: leon
    password: ServBay.dev
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### Task 4: JPA Entity 类
**File:** `src/main/java/com/example/ruleengine/domain/Rule.java`

创建 Rule 实体类：
- 使用 `@Entity`, `@Table`, `@Id`, `@GeneratedValue`
- 使用 Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- 配置 JPA 映射（`@Column`, `@Enumerated`, `@Temporal`）
- 添加索引注解（`@Index`）

### Task 5: Repository 接口
**File:** `src/main/java/com/example/ruleengine/repository/RuleRepository.java`

创建 Spring Data JPA Repository：
- 继承 `JpaRepository<Rule, Long>`
- 添加自定义查询方法：
  - `findByRuleKey(String ruleKey)`
  - `findByStatus(RuleStatus status)`
  - `findByEnabledTrue()`
  - `existsByRuleKey(String ruleKey)`

### Task 6: 单元测试
**File:** `src/test/java/com/example/ruleengine/repository/RuleRepositoryTest.java`

测试 Repository 功能：
- 使用 `@DataJpaTest` 和 Testcontainers
- 测试 CRUD 操作
- 测试自定义查询方法
- 测试唯一约束

## 验证策略

### 手动验证
1. 启动应用，检查 Flyway 迁移是否成功
2. 使用 pgAdmin 或 psql 验证表结构
3. 插入测试数据，验证 JPA 映射正确

### 自动化测试
```bash
# 运行单元测试
./gradlew test --tests RuleRepositoryTest

# 验证数据库连接
./gradlew bootRun
curl http://localhost:8080/actuator/health
```

## 成功标准

- [ ] `yare_engine` 数据库创建成功
- [ ] `rules` 表创建成功，所有字段和索引正确
- [ ] Flyway 迁移成功执行
- [ ] Rule Entity 和 Repository 创建完成
- [ ] 所有单元测试通过
- [ ] 应用启动成功，JPA 配置无错误

## 输出产物

- `V1__init_database.sql` - 数据库迁移脚本
- `Rule.java` - JPA 实体类
- `RuleRepository.java` - 数据访问接口
- `application.yml` - 更新后的配置文件
- `build.gradle` - 更新后的依赖配置
- `RuleRepositoryTest.java` - 单元测试

## 风险与注意事项

1. **数据库连接失败**: 确保 PostgreSQL 服务运行，网络可达
2. **权限不足**: 确保用户 `leon` 有创建数据库和表的权限
3. **Flyway 迁移冲突**: 使用 `baseline-on-migrate: true` 避免与现有数据冲突
4. **JPA 性能**: 后续需要关注 N+1 查询问题，考虑使用 `@EntityGraph`

## 后续依赖

- ✅ 完成后可开始 **02-02: 规则版本管理**
- ✅ 完成后可开始 **02-03: 审计日志系统**

## 相关文档

- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- Flyway: https://flywaydb.org/documentation/
- PostgreSQL JDBC: https://jdbc.postgresql.org/
