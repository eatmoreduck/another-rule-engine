# 路线图: 低代码风控规则引擎

**Created:** 2025-03-26
**Granularity:** Fine
**Coverage:** 27/27 requirements mapped

## Phases

- [ ] **Phase 1: 核心规则执行引擎** - 建立稳定、安全、高性能的规则执行基础设施
- [ ] **Phase 2: 数据持久化与版本管理** - 规则存储、版本控制、审计日志
- [ ] **Phase 3: 规则配置界面** - 可视化和表单配置能力
- [ ] **Phase 4: 监控与安全增强** - 监控分析、沙箱安全、性能优化
- [ ] **Phase 5: 灰度发布与异步执行** - 灰度机制、异步事件驱动
- [ ] **Phase 6: 测试验证与分析** - 测试工具、冲突检测、依赖分析
- [ ] **Phase 7: 高级功能与扩展** - 决策表、多环境、导入导出

## Phase Details

### Phase 1: 核心规则执行引擎

**Goal**: 建立稳定、安全、高性能的规则执行基础设施，确保规则在 50ms 内完成执行并具备完善的安全防护机制

**Depends on**: Nothing（第一优先级，基础设施）

**Requirements**: REXEC-01, REXEC-03, REXEC-04, PERF-01, PERF-02, SEC-02

**Success Criteria** (what must be TRUE):
1. 用户可通过同步 API 在 50ms 内获得规则决策结果
2. 规则以 Groovy DSL 形式动态加载并执行成功
3. 特征获取优先使用入参，失败时可降级到外部特征平台
4. 系统对规则执行进行超时控制，防止单个规则影响整体性能
5. Groovy 脚本通过缓存机制避免重复编译，防止内存泄漏

**Plans**: 5 plans

Plan 1: 项目基础架构搭建（Wave 1）
- 创建 Gradle 多模块项目结构
- 配置 Java 21 + Spring Boot 3.3.x + Groovy 4.0.22
- 启用虚拟线程和核心依赖
- 建立 Spring Boot 应用框架

Plan 2: Groovy 脚本执行引擎（Wave 1）
- 实现 GroovyScriptEngine 脚本执行引擎
- 实现 ScriptCacheManager 脚本缓存管理
- 实现 SecurityConfiguration 沙箱安全配置
- 实现 ClassLoaderManager 类加载器管理
- 单元测试覆盖

Plan 3: 特征获取服务（Wave 2）
- 实现 FeatureProviderService 特征获取服务
- 实现 CaffeineCacheConfig 缓存配置
- 实现三级策略（入参 → 外部 → 默认值）
- 实现特征预加载和批量获取
- 单元测试覆盖

Plan 4: 规则执行服务（Wave 2）
- 实现 RuleExecutionService 规则执行服务
- 实现 Resilience4jConfig 配置
- 实现 DecisionRequest/DecisionResponse 模型
- 集成脚本执行和特征获取
- 单元测试覆盖

Plan 5: 同步决策 API（Wave 3）
- 实现 DecisionController 决策 API 控制器
- 配置 Actuator 和监控
- 实现 POST /api/v1/decide 端点
- 单元测试和集成测试
- 性能验证（P95 < 50ms）

### Phase 2: 数据持久化与版本管理

**Goal**: 实现规则元数据持久化、多版本管理和完整的审计追溯能力

**Depends on**: Phase 1（需要在规则执行稳定后建立持久化层）

**Requirements**: PERS-01, PERS-02, PERS-03, VER-01, VER-02

**Success Criteria** (what must be TRUE):
1. 规则元数据成功持久化到 PostgreSQL 数据库
2. 规则变更历史完整记录，支持按时间追溯
3. 审计日志记录所有用户操作（谁、何时、做了什么）
4. 用户可创建规则的新版本并查看所有历史版本
5. 用户可一键回滚规则到任意历史版本

**Plans**: 5 plans

**Wave 1 - 基础设施:**
- Plan 02-01: 数据库基础设施搭建
  - 创建数据库和表结构（rules, rule_versions, audit_logs）
  - 配置 JPA 和 Flyway
  - 创建 Rule Entity 和 Repository

**Wave 2 - 核心功能:**
- Plan 02-02: 规则版本管理
  - 创建版本历史表和 Entity
  - 实现 VersionManagementService（创建版本、回滚版本）
  - 提供版本管理 REST API

- Plan 02-03: 审计日志系统
  - 创建审计日志表和 Entity
  - 实现 AuditLogService
  - 使用 AOP 和 @Auditable 注解自动记录日志

**Wave 3 - 业务功能和优化:**
- Plan 02-04: 规则生命周期管理
  - 实现 RuleLifecycleService（创建、编辑、启用/禁用、删除）
  - 集成 RuleExecutionService，从数据库加载规则
  - 提供规则管理 REST API

- Plan 02-05: 性能优化与缓存
  - 设计三级缓存策略（编译脚本、规则元数据、Redis）
  - 实现 RuleCacheService 和缓存预热
  - 数据库查询优化和性能测试

### Phase 3: 规则配置界面

**Goal**: 提供可视化和表单两种配置方式，让业务人员能够独立配置规则

**Depends on**: Phase 2（需要版本管理和持久化支持配置保存）

**Requirements**: RCONF-01, RCONF-02, UI-01, UI-02, UI-03

**Success Criteria** (what must be TRUE):
1. 用户可通过拖拽式可视化界面配置复杂规则流程（条件分支、动作、阈值）
2. 用户可通过表单快速配置简单规则（字段比较、数值范围等）
3. 前后端分离架构，前端独立部署
4. 用户可通过 Web 界面管理规则（列表、详情、编辑、预览）
5. 配置的规则可成功保存并转换为 Groovy DSL 格式

**Plans**: TBD

**UI hint**: yes

### Phase 4: 监控与安全增强

**Goal**: 建立完善的监控体系和沙箱安全机制，确保系统可观测性和安全性

**Depends on**: Phase 1（需要基于规则执行引擎添加监控和安全防护）

**Requirements**: MON-01, MON-02, SEC-01

**Success Criteria** (what must be TRUE):
1. 系统记录规则命中统计（执行次数、命中次数）
2. 系统记录规则执行日志（输入、输出、执行时间）
3. 规则在沙箱环境中执行，防止恶意代码攻击
4. 用户可查看规则的实时执行情况和性能指标
5. 沙箱机制成功拦截危险操作（文件访问、网络调用、进程执行）

**Plans**: TBD

### Phase 5: 灰度发布与异步执行

**Goal**: 实现规则灰度发布能力和异步事件驱动执行模式，降低上线风险并扩展业务场景

**Depends on**: Phase 2（需要版本管理支持灰度）和 Phase 1（需要基础执行引擎）

**Requirements**: REXEC-02, REXEC-05, VER-03, VER-04

**Success Criteria** (what must be TRUE):
1. 系统支持规则灰度发布，可按百分比切换流量
2. 灰度发布期间，系统记录不同版本的表现数据用于对比
3. 系统支持异步事件驱动执行模式（消息队列）
4. 用户可监控灰度规则的执行效果并决定是否继续放量
5. 异步执行模式下规则正确处理事件消息并返回结果

**Plans**: TBD

### Phase 6: 测试验证与分析

**Goal**: 提供规则测试验证工具和智能分析能力，提升规则质量和可维护性

**Depends on**: Phase 1（需要规则执行引擎支持测试）和 Phase 3（需要配置界面集成测试功能）

**Requirements**: TEST-01, TEST-02, TEST-03, MON-03, MON-04

**Success Criteria** (what must be TRUE):
1. 用户可在规则上线前使用模拟数据进行测试验证
2. 系统提供规则执行的实时调试功能
3. 系统自动检测规则之间的逻辑冲突并给出警告
4. 系统提供规则效果分析（命中率、误判率、拦截率）
5. 系统分析规则之间的依赖关系并展示依赖图

**Plans**: TBD

**UI hint**: yes

### Phase 7: 高级功能与扩展

**Goal**: 提供决策表、多环境隔离和导入导出等企业级功能，增强产品竞争力

**Depends on**: Phase 2（需要持久化支持多环境）和 Phase 3（需要配置界面支持高级功能）

**Requirements**: ADV-01, ADV-02, ADV-03, RCONF-03, RCONF-04

**Success Criteria** (what must be TRUE):
1. 系统支持决策表，用于多维度条件组合场景
2. 系统支持多环境隔离（开发、测试、生产）
3. 用户可导入导出规则，便于跨系统迁移
4. 系统提供规则模板库，预置常见反欺诈规则模板
5. 用户可保存规则为个人模板，便于复用

**Plans**: TBD

**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. 核心规则执行引擎 | 5/5 | ✅ Complete | 2025-03-26 |
| 2. 数据持久化与版本管理 | 0/5 | 📋 Planned | - |
| 3. 规则配置界面 | 0/0 | Not started | - |
| 4. 监控与安全增强 | 0/0 | Not started | - |
| 5. 灰度发布与异步执行 | 0/0 | Not started | - |
| 6. 测试验证与分析 | 0/0 | Not started | - |
| 7. 高级功能与扩展 | 0/0 | Not started | - |

## Dependencies

```
Phase 1 (核心规则执行引擎)
    ↓
Phase 2 (数据持久化与版本管理) ← Phase 1
    ↓
Phase 3 (规则配置界面) ← Phase 2
    ↓
Phase 4 (监控与安全增强) ← Phase 1
Phase 5 (灰度发布与异步执行) ← Phase 1, Phase 2
Phase 6 (测试验证与分析) ← Phase 1, Phase 3
Phase 7 (高级功能与扩展) ← Phase 2, Phase 3
```

## Key Milestones

1. **Phase 1 完成**: 规则引擎核心可用，50ms 性能目标达成
2. **Phase 2 完成**: 规则可持久化并支持版本管理
3. **Phase 3 完成**: 业务人员可独立配置规则（核心价值达成）
4. **Phase 5 完成**: 灰度发布能力上线，降低规则变更风险
5. **Phase 7 完成**: 所有 v1 功能交付完成

---
*Roadmap created: 2025-03-26*
*Last updated: 2025-03-26*
