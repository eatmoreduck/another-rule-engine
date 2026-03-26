# Phase 1: 核心规则执行引擎 - Context

**Gathered:** 2025-03-26 (assumptions mode)
**Status:** Ready for planning

## Phase Boundary

建立稳定、安全、高性能的规则执行基础设施。包括：Groovy 脚本动态加载执行、同步 API 决策接口、特征获取多策略支持、脚本缓存防内存泄漏、超时控制防服务雪崩。不包含规则持久化、版本管理、UI 配置界面（后续阶段）。

## Implementation Decisions

### 项目结构与构建
- **D-01:** 使用 Gradle 8.5+ 作为构建工具，Groovy DSL 配置
- **D-02:** 使用标准的 Spring Boot 3.3.x 多模块项目结构（当前仅需 backend 模块）
- **D-03:** Java 21 LTS + Groovy 4.0.x/5.x，启用 Spring Boot 虚拟线程

### Groovy 脚本执行引擎
- **D-04:** 使用 GroovyShell 动态编译和执行 Groovy 脚本
- **D-05:** 使用 ConcurrentHashMap 实现线程安全的脚本缓存，避免重复编译
- **D-06:** 使用单例 GroovyClassLoader，定期清理缓存防止 Metaspace 泄漏
- **D-07:** 使用 CompilerConfiguration 启用 PARALLEL_PARSE 提升编译性能

### 安全防护
- **D-08:** 使用 CompilerConfiguration 自定义沙箱，禁用危险类（System.class、Runtime.class、ProcessBuilder.class、File.class）
- **D-09:** 使用 SecurityManager 白名单机制，只允许规则访问特定类和方法
- **D-10:** 脚本在独立 ClassLoader 中执行，限制父类加载器访问

### 性能与稳定性
- **D-11:** 使用 Resilience4j 实现规则执行超时控制，超时时间 50ms
- **D-12:** 使用独立线程池执行规则，隔离风险
- **D-13:** 使用 Caffeine 实现本地缓存（高频特征、编译后的脚本）

### 特征获取策略
- **D-14:** 实现 "入参优先 → 外部降级 → 默认值" 三级策略
- **D-15:** 特征获取支持超时和降级，超时返回默认值保证系统响应

### API 设计
- **D-16:** 同步 API 使用 POST /api/v1/decide，Content-Type: application/json
- **D-17:** 请求格式：{ "ruleId": "string", "features": { "key": "value" } }
- **D-18:** 响应格式：{ "decision": "PASS/REJECT", "reason": "string", "executionTimeMs": 50 }
- **D-19:** API 响应时间要求 < 50ms（P95）

### Claude's Discretion
- Spring Boot Actuator 端点配置细节
- 日志格式和级别配置
- 单元测试覆盖率目标
- 集成测试框架选型（JUnit 5 + Testcontainers 或其他）

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 项目规范
- `.planning/PROJECT.md` — 项目愿景、核心价值、技术栈约束
- `.planning/REQUIREMENTS.md` — v1 需求定义，Phase 1 对应需求 REXEC-01, REXEC-03, REXEC-04, PERF-01, PERF-02, SEC-02
- `.planning/ROADMAP.md` — Phase 1 详细定义、成功标准、依赖关系

### 技术栈规范
- `.planning/research/STACK.md` — 推荐技术栈、版本选择、依赖配置示例
- `.planning/research/PITFALLS.md` — 必须避免的关键陷阱（内存泄漏、沙箱绕过、执行超时）

### 架构规范
- `.planning/research/ARCHITECTURE.md` — 五层分层架构、组件边界、数据流

## Existing Code Insights

### Reusable Assets
（全新项目，无现有代码）

### Established Patterns
（全新项目，使用研究文档推荐的标准模式）

### Integration Points
- 外部特征平台：HTTP 客户端调用，需实现超时和降级
- PostgreSQL：Phase 2 集成，当前阶段不涉及
- Redis：Phase 5 集成，当前阶段使用 Caffeine 本地缓存

## Specific Ideas

基于研究文档和项目需求的具体实现建议：

1. **脚本缓存 Key 设计：** 使用 ruleId:version 作为缓存键，相同版本的规则复用已编译的 Class
2. **超时策略：** Resilience4j 的 CircuitBreaker + ThreadPoolIsolation，防止级联故障
3. **特征获取降级：** 使用 CompletableFuture.anyOf() 实现超时控制，默认值从配置文件读取
4. **安全检查点：** 在脚本编译前进行 AST 静态分析，检测危险调用模式
5. **性能监控点：** 使用 Micrometer 记录规则执行耗时、缓存命中率、特征获取耗时

## Deferred Ideas

### Phase 2 内容
- 规则元数据持久化到 PostgreSQL
- 规则版本管理和变更历史
- 审计日志记录

### Phase 4 内容
- 沙箱安全机制增强（Phase 1 实现基础沙箱）
- 规则执行监控和日志记录

### Phase 5 内容
- 异步事件执行模式
- 灰度发布机制

### 后续版本
- AI 辅助规则生成
- 机器学习模型集成
- 实时流处理

---

*Phase: 01-core-engine*
*Context gathered: 2025-03-26*
