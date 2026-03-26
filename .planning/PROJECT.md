# 低代码风控规则引擎

## What This Is

一个面向电商反欺诈场景的低代码规则引擎，支持通过可视化和表单配置定义业务规则，通过同步/异步混合模式执行规则决策，并提供完整的版本管理、灰度发布和回滚能力。核心目标是让非技术人员也能快速配置和修改风控规则，响应毫秒级决策需求。

## Core Value

**业务人员可独立配置风控规则，50ms 内返回决策结果。**

如果规则配置太复杂、执行太慢、或者需要开发介入，这个产品就失败了。

## Requirements

### Validated

（尚未发布，ship 后验证）

### Active

- [ ] 用户可通过可视化界面配置风控规则（条件分支、动作、阈值）
- [ ] 用户可通过表单配置简单规则（字段比较、数值范围等）
- [ ] 规则支持同步 API 调用执行
- [ ] 规则支持异步事件驱动执行
- [ ] 特征获取支持多策略：入参优先，可降级到外部特征平台
- [ ] 规则支持多版本管理
- [ ] 规则支持灰度发布（按比例流量切换）
- [ ] 规则支持一键回滚到历史版本
- [ ] 单次规则执行响应时间 < 50ms
- [ ] 规则以 Groovy DSL 形式存储和动态加载
- [ ] 规则元数据持久化到 PostgreSQL（JPA）
- [ ] 前后端分离架构
- [ ] 预留复杂计算逻辑的扩展点（不在此项目实现）

### Out of Scope

- **AI 辅助规则生成** — 后续版本再做
- **复杂计算逻辑** — 预留扩展点，计算在外部系统完成
- **机器学习模型集成** — 后续版本考虑
- **实时流处理** — v1 仅支持同步 API + 异步事件，不做流式计算
- **移动端管理界面** — v1 仅 Web 端

## Context

电商反欺诈场景通常需要快速响应新的欺诈手段。传统硬编码规则开发周期长、测试成本高、上线风险大。通过低代码配置，业务人员可以根据欺诈模式的变化快速调整规则，无需开发介入。

风控决策对性能要求极高，需要在支付链路中实时完成，不能引入明显延迟。同时规则变更风险高，需要灰度发布和快速回滚能力。

## Constraints

- **技术栈**: Groovy + Spring Boot + PostgreSQL + JPA — 团队熟悉度，Groovy 动态能力适合规则引擎
- **架构**: 前后端分离 — 便于前端独立迭代，支持多终端
- **性能**: 单次决策 < 50ms — 支付链路实时性要求
- **部署**: 标准 JVM 部署环境 — 兼容现有运维体系

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 规则定义：可视化 + 表单 | 不同复杂度规则需要不同配置方式，可视化适合复杂流程，表单适合简单条件 | — Pending |
| 规则执行：同步/异步混合 | 同步满足实时决策需求，异步满足非实时场景 | — Pending |
| 特征获取：入参优先，降级外部 | 减少外部依赖，提升性能，同时保持灵活性 | — Pending |
| 规则版本管理：必须支持 | 风控规则变更风险高，需要灰度和回滚能力 | — Pending |
| 规则存储：Groovy DSL | Groovy 动态能力强，语法简洁，JVM 生态兼容 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2025-03-26 after initialization*
