# Requirements: 低代码风控规则引擎

**Defined:** 2025-03-26
**Core Value:** 业务人员可独立配置风控规则，50ms 内返回决策结果。

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### 规则配置 (RULE-CONFIG)

- [ ] **RCONF-01**: 用户可通过拖拽式可视化界面配置复杂规则流程（条件分支、动作、阈值）
- [ ] **RCONF-02**: 用户可通过表单快速配置简单规则（字段比较、数值范围等）
- [ ] **RCONF-03**: 系统支持规则模板库，预置常见反欺诈规则模板
- [ ] **RCONF-04**: 用户可保存规则为个人模板，便于复用

### 规则执行 (RULE-EXEC)

- [ ] **REXEC-01**: 系统通过同步 API 接收决策请求，在 50ms 内返回结果
- [ ] **REXEC-02**: 系统支持异步事件驱动执行模式（消息队列）
- [ ] **REXEC-03**: 规则以 Groovy DSL 形式存储和动态加载执行
- [ ] **REXEC-04**: 特征获取支持多策略：入参优先，可选降级到外部特征平台
- [ ] **REXEC-05**: 系统对规则执行进行超时控制，防止单个规则影响整体性能

### 版本管理 (VERSION-MGMT)

- [ ] **VER-01**: 系统支持规则多版本管理，可创建、修改、删除版本
- [ ] **VER-02**: 用户可一键回滚规则到任意历史版本
- [ ] **VER-03**: 系统支持规则灰度发布，可按百分比切换流量
- [ ] **VER-04**: 灰度发布期间，系统记录不同版本的表现数据用于对比

### 数据持久化 (PERSISTENCE)

- [ ] **PERS-01**: 规则元数据持久化到 PostgreSQL（使用 JPA）
- [ ] **PERS-02**: 规则变更历史完整记录，支持追溯
- [ ] **PERS-03**: 审计日志记录所有用户操作（谁、何时、做了什么）

### 测试与验证 (TESTING)

- [ ] **TEST-01**: 用户可在规则上线前使用模拟数据进行测试验证
- [ ] **TEST-02**: 系统提供规则执行的实时调试功能
- [ ] **TEST-03**: 系统自动检测规则之间的逻辑冲突

### 监控与分析 (MONITORING)

- [ ] **MON-01**: 系统记录规则命中统计（执行次数、命中次数）
- [ ] **MON-02**: 系统记录规则执行日志（输入、输出、执行时间）
- [ ] **MON-03**: 系统提供规则效果分析（命中率、误判率、拦截率）
- [ ] **MON-04**: 系统分析规则之间的依赖关系

### 高级功能 (ADVANCED)

- [ ] **ADV-01**: 系统支持决策表，用于多维度条件组合场景
- [ ] **ADV-02**: 系统支持多环境隔离（开发、测试、生产）
- [ ] **ADV-03**: 用户可导入导出规则，便于跨系统迁移

### 安全与性能 (SECURITY-PERF)

- [ ] **SEC-01**: 规则在沙箱环境中执行，防止恶意代码
- [ ] **SEC-02**: 系统对 Groovy 脚本进行类加载管理，防止内存泄漏
- [ ] **PERF-01**: 系统使用脚本缓存机制提升规则执行性能
- [ ] **PERF-02**: 系统使用特征预加载和批量获取优化性能

### 前端界面 (FRONTEND)

- [ ] **UI-01**: 系统提供基于 Web 的规则管理界面
- [ ] **UI-02**: 前后端分离架构，前端独立部署
- [ ] **UI-03**: 界面支持规则列表、详情、编辑、预览等操作

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### AI 辅助

- **AI-01**: 系统支持 AI 辅助规则生成
- **AI-02**: 系统根据自然语言描述推荐规则配置

### 扩展功能

- **EXT-01**: 系统支持机器学习模型集成
- **EXT-02**: 系统支持实时流处理（Kafka）
- **EXT-03**: 系统提供移动端管理界面

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| 工作流引擎功能 | 规则引擎专注条件判断，流程编排应使用专门的 BPM 工具 |
| 复杂计算逻辑 | 预留扩展点，计算在外部系统完成，保持规则引擎简洁 |
| 分布式规则执行 | 单机多线程性能足够，避免过早引入分布式复杂度 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| RCONF-01 | Phase 3 | Pending |
| RCONF-02 | Phase 3 | Pending |
| RCONF-03 | Phase 7 | Pending |
| RCONF-04 | Phase 7 | Pending |
| REXEC-01 | Phase 1 | Pending |
| REXEC-02 | Phase 5 | Pending |
| REXEC-03 | Phase 1 | Pending |
| REXEC-04 | Phase 1 | Pending |
| REXEC-05 | Phase 5 | Pending |
| VER-01 | Phase 2 | Pending |
| VER-02 | Phase 2 | Pending |
| VER-03 | Phase 5 | Pending |
| VER-04 | Phase 5 | Pending |
| PERS-01 | Phase 2 | Pending |
| PERS-02 | Phase 2 | Pending |
| PERS-03 | Phase 2 | Pending |
| TEST-01 | Phase 6 | Pending |
| TEST-02 | Phase 6 | Pending |
| TEST-03 | Phase 6 | Pending |
| MON-01 | Phase 4 | Pending |
| MON-02 | Phase 4 | Pending |
| MON-03 | Phase 6 | Pending |
| MON-04 | Phase 6 | Pending |
| ADV-01 | Phase 7 | Pending |
| ADV-02 | Phase 7 | Pending |
| ADV-03 | Phase 7 | Pending |
| SEC-01 | Phase 4 | Pending |
| SEC-02 | Phase 1 | Pending |
| PERF-01 | Phase 1 | Pending |
| PERF-02 | Phase 1 | Pending |
| UI-01 | Phase 3 | Pending |
| UI-02 | Phase 3 | Pending |
| UI-03 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0 ✓

**Phase Distribution:**
- Phase 1 (核心规则执行引擎): 6 requirements
- Phase 2 (数据持久化与版本管理): 5 requirements
- Phase 3 (规则配置界面): 5 requirements
- Phase 4 (监控与安全增强): 3 requirements
- Phase 5 (灰度发布与异步执行): 4 requirements
- Phase 6 (测试验证与分析): 5 requirements
- Phase 7 (高级功能与扩展): 5 requirements

---
*Requirements defined: 2025-03-26*
*Last updated: 2025-03-26 after roadmap creation*
