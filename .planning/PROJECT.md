# 低代码风控规则引擎

## What This Is

一个面向电商反欺诈场景的低代码规则引擎，支持通过可视化和表单配置定义业务规则，通过同步/异步混合模式执行规则决策，并提供完整的版本管理、灰度发布和回滚能力。已实现 16 个 REST Controller、15+ Service 包、9 个 Domain 实体、8 个数据库迁移、12 个前端页面。

## Core Value

**业务人员可独立配置风控规则，50ms 内返回决策结果。**

如果规则配置太复杂、执行太慢、或者需要开发介入，这个产品就失败了。

## Current State

**Shipped:** v1.0 MVP (2026-03-31)
**Tech Stack:** Java 17 + Spring Boot 3.3 + Groovy 4.0.22 + PostgreSQL + React 19
**LOC:** 16,538 Java + 7,101 TypeScript = 23,639 total
**Test Coverage:** 254+ unit/integration tests

## Requirements

### Validated

- ✓ 可视化界面配置风控规则 — v1.0 (React Flow 流程图编辑器)
- ✓ 表单配置简单规则 — v1.0 (条件-动作模式)
- ✓ 同步 API 规则执行 — v1.0 (POST /api/v1/decide)
- ✓ 缓存感知规则执行 — v1.0 (POST /api/v1/decide/{ruleKey})
- ✓ 异步事件驱动执行 — v1.0 (AsyncRuleExecutionService)
- ✓ 特征获取三级策略 — v1.0 (入参 → 外部 → 默认值)
- ✓ 规则多版本管理 — v1.0 (VersionManagementService)
- ✓ 灰度发布 — v1.0 (GrayscaleService, 自动扩量+回滚)
- ✓ 规则持久化 — v1.0 (PostgreSQL + JPA + Flyway)
- ✓ 监控与执行日志 — v1.0 (Prometheus + ExecutionLogService)
- ✓ 安全沙箱 — v1.0 (SecurityConfiguration + 7类安全测试)
- ✓ 规则测试验证 — v1.0 (TestExecutionService + 冲突检测)
- ✓ 规则分析 — v1.0 (RuleAnalyticsService + 依赖分析)
- ✓ 决策表 — v1.0 (DecisionTableService)
- ✓ 多环境隔离 — v1.0 (EnvironmentService, DEV/STAGING/PROD)
- ✓ 导入导出 — v1.0 (RuleImportExportService)
- ✓ 规则模板库 — v1.0 (RuleTemplateService + 个人模板)

### Active

(Planning next milestone)

### Out of Scope

| Feature | Reason |
|---------|--------|
| AI 辅助规则生成 | 后续版本再做 |
| 复杂计算逻辑 | 预留扩展点，计算在外部系统完成 |
| 机器学习模型集成 | 后续版本考虑 |
| 实时流处理 | v1 仅支持同步 API + 异步事件 |
| 移动端管理界面 | v1 仅 Web 端 |
| 分布式规则执行 | 单机多线程性能足够 |

## Context

v1.0 已交付完整的规则引擎 MVP，包含后端 Java 服务和前端 React 管理界面。系统支持规则的可视化/表单配置、同步/异步执行、版本管理、灰度发布、监控分析等企业级功能。

### Known Tech Debt

1. DegradationService 导出但未注入（逻辑内联在 AsyncRuleExecutionService）
2. CacheWarmer 启动调用未验证
3. Phase 3 的 03-02-PLAN 缺少 SUMMARY.md（代码已实现）
4. 性能目标 50ms 尚未压测验证

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Groovy DSL 规则存储 | 动态能力强，语法简洁，JVM 生态兼容 | ✓ Good |
| 同步/异步混合执行 | 覆盖实时和非实时场景 | ✓ Good |
| React 19 + Ant Design 前端 | 企业级 UI 组件，适合复杂表单 | ✓ Good |
| PostgreSQL + Flyway | 成熟稳定，JSONB 支持，迁移可控 | ✓ Good |
| Resilience4j 熔断 | 轻量级，Spring Boot 3 原生支持 | ✓ Good |
| 多环境数据库隔离 | 简单有效，避免跨环境污染 | ⚠️ 可扩展为 Redis 隔离 |

## Constraints

- **技术栈**: Java 17 + Spring Boot 3.3 + Groovy 4.0.22 + PostgreSQL
- **架构**: 前后端分离 (React + Spring Boot)
- **性能**: 单次决策 < 50ms (待压测验证)
- **部署**: 标准 JVM 部署环境

---
*Last updated: 2026-03-31 after v1.0 milestone*
