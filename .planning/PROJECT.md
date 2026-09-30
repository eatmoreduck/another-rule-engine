# 低代码风控规则引擎

## What This Is

一个面向电商反欺诈场景的低代码规则引擎，支持通过可视化和表单配置定义业务规则，通过同步/异步混合模式执行规则决策，并提供完整的版本管理、灰度发布和回滚能力。当前代码库已包含 21 个 REST Controller、18 个 Domain 实体、18 个 Repository、17 个前端页面，以及权限控制、多环境和导入导出等运营能力。

## Core Value

**业务人员可独立配置风控规则，50ms 内返回决策结果。**

如果规则配置太复杂、执行太慢、或者需要开发介入，这个产品就失败了。

## Current State

**Shipped:** v1.0 MVP (2026-03-31) + Phase 8 权限控制扩展 (2026-04-04)
**Tech Stack:** Java 17 + Spring Boot 3.3 + Groovy 4.0.22 + PostgreSQL + React 19
**Codebase:** 21 Controllers + 18 Domain Entities + 18 Repositories + 17 Frontend Pages
**Current Gap:** 已具备“规则配置与执行”能力，但尚未形成“风控运营闭环”——缺少特征治理、历史回放、人工审核、发布审批与业务收益分析。

## Current Milestone: v1.1 风控运营闭环

**Goal:** 将产品从“低代码规则引擎”升级为“可验证、可审核、可发布、可复盘的风控运营平台”。

**Target features:**
- 特征字典：特征定义、类型、来源、示例值、敏感级别、别名兼容、编辑器直连
- 人工审核闭环：工单池、审核结论、标签回写、SLA、二次学习输入
- 回放仿真：历史样本集、批量重跑、差异分析、误杀风险与收益影响评估
- 发布治理：发布单、审批流、变更说明、环境晋级、证据包、可回滚
- 业务指标分析：按规则/版本/环境/分层查看 GMV、订单量、误杀率、欺诈率
- 策略模板库：场景模板、参数化实例化、依赖特征提示、上线建议

## Requirements

### Validated

- ✓ 可视化界面配置风控规则 — v1.0 (React Flow 流程图编辑器)
- ✓ 表单配置简单规则 — v1.0 (条件-动作模式)
- ✓ 同步 API 规则执行 — v1.0 (POST /api/v1/decide)
- ✓ 异步事件驱动执行 — v1.0 (AsyncRuleExecutionService)
- ✓ 特征获取三级策略 — v1.0 (入参 → 外部 → 默认值)
- ✓ 规则多版本管理 — v1.0 (VersionManagementService)
- ✓ 灰度发布 — v1.0 (GrayscaleService, 自动扩量 + 回滚)
- ✓ 规则持久化 — v1.0 (PostgreSQL + JPA + Flyway)
- ✓ 监控与执行日志 — v1.0 (Prometheus + ExecutionLogService)
- ✓ 规则测试验证与依赖分析 — v1.0 (TestExecutionService + RuleAnalyticsService + RuleDependencyAnalyzer)
- ✓ 权限控制 — Phase 8 (Sa-Token + 用户/角色/权限管理)
- ✓ 多环境隔离 — v1.0 (EnvironmentService)
- ✓ 导入导出 — v1.0 (RuleImportExportService)

### Active

- 规划并落地 v1.1 风控运营闭环需求，优先构建“特征字典 -> 审核反馈 -> 回放仿真 -> 发布治理”的基础链路
- 将业务指标分析从技术指标扩展到经营指标（GMV、欺诈率、误杀率、追回金额）
- 补齐模板库能力，使业务人员从“空白配置”切换为“模板化起步”

### Out of Scope

| Feature | Reason |
|---------|--------|
| AI 辅助规则生成 | 先完成运营闭环，避免在没有标签和模板沉淀前引入生成式复杂度 |
| 机器学习模型编排 | 当前里程碑聚焦规则平台闭环，不进入模型 Serving / 特征工程平台 |
| 实时流计算平台 | 样本回放与业务分析优先基于现有数据库与批处理能力实现 |
| 外部工单系统双向同步 | 先完成内建审核闭环，再考虑与 CRM / 工单平台集成 |
| 自动化灰度放量策略优化 | 先把发布治理和回放证据链建好，再做自动决策 |

## Context

v1.0 已交付规则配置、执行、灰度、监控、权限等基础能力，但用户仍需研发协助完成策略验证、人工审核、发布审批和业务归因分析。v1.1 的核心任务是把这些“运营闭环能力”纳入统一产品工作流，让规则变更具备数据证据、审核路径和收益复盘。

### Known Tech Debt

1. `ExecutionLog` 缺少业务事实字段（订单金额、分层、标签结果、收益影响）
2. `MANUAL_REVIEW` 仅为决策枚举值，尚未形成工单和反馈闭环
3. `FeatureProviderService` 仅负责运行时获取特征，缺少特征元数据治理与兼容映射
4. `README` / `.planning` 部分文档对已实现能力描述偏乐观，需在开发过程中同步校正

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 先做特征字典，再做模板/回放 | 没有统一特征定义，模板、校验和回放都会失真 | v1.1 Phase 9 |
| 先做审核闭环，再算误杀/欺诈率 | 没有真值标签，业务分析只能停留在命中率和拦截率 | v1.1 Phase 10 |
| 回放仿真基于历史样本快照 | 发布前必须先看差异和收益估算，不能只靠灰度试错 | v1.1 Phase 11 |
| 发布治理必须绑定证据包 | 生产发布需要审批、回放结果和回滚计划，不允许裸发布 | v1.1 Phase 12 |
| 业务分析晚于回放与反馈 | 指标体系依赖样本、标签和发布记录，先有事实再有看板 | v1.1 Phase 13 |
| 模板库最后落地 | 模板要复用前面沉淀的特征、回放、发布建议和指标口径 | v1.1 Phase 14 |

## Constraints

- **技术栈**: Java 17 + Spring Boot 3.3 + Groovy 4.0.22 + PostgreSQL + React 19
- **架构**: 前后端分离 (React + Spring Boot)
- **性能**: 在线决策主链路仍需维持 < 50ms，回放/分析走异步或批处理
- **部署**: 标准 JVM 部署环境，优先复用现有 PostgreSQL 与前端架构
- **兼容性**: 新能力必须兼容现有规则 DSL 和已发布规则，不允许强制迁移老规则

---
*Last updated: 2026-04-10 after starting milestone v1.1*
