# 路线图: 低代码风控规则引擎

**Created:** 2025-03-26
**Last updated:** 2026-04-10
**Current milestone coverage:** 23/23 requirements mapped
**Phase numbering note:** Phase 8 已用于权限控制扩展，因此 v1.1 从 Phase 9 继续编号。

## Milestones

- ✅ **v1.0 MVP** — Phases 1-8 (基础规则平台 + 权限控制，shipped 2026-04-04)
- 🚧 **v1.1 风控运营闭环** — Phases 9-14 (planning)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-8) — SHIPPED</summary>

- [x] Phase 1: 核心规则执行引擎 — 规则执行、特征获取、性能与安全基础设施
- [x] Phase 2: 数据持久化与版本管理 — 规则存储、版本控制、审计日志
- [x] Phase 3: 规则配置界面 — 表单 + 流程图双模式配置
- [x] Phase 4: 监控与安全增强 — Prometheus、执行日志、沙箱测试
- [x] Phase 5: 灰度发布与异步执行 — 灰度引擎、异步执行、回滚
- [x] Phase 6: 测试验证与分析 — 测试执行、冲突检测、依赖分析
- [x] Phase 7: 高级功能与扩展 — 多环境、导入导出等高级能力
- [x] Phase 8: 权限控制 — 登录、角色权限、用户/角色管理

</details>

<details open>
<summary>🚧 v1.1 风控运营闭环 (Phases 9-14) — PLANNED</summary>

- [ ] **Phase 9: 特征字典与规则兼容底座** - 建立可治理的特征元数据体系，并与现有规则 DSL 保持兼容
- [ ] **Phase 10: 人工审核闭环与标签反馈** - 将 `MANUAL_REVIEW` 升级为完整工单、审核和反馈闭环
- [ ] **Phase 11: 历史样本回放与仿真评估** - 基于样本集批量重跑规则版本，输出差异、风险和收益估算
- [ ] **Phase 12: 发布治理与环境晋级** - 建立发布单、审批流、证据包和环境晋级/回滚治理
- [ ] **Phase 13: 业务指标分析与复盘看板** - 将分析能力从技术指标扩展到 GMV、误杀率、欺诈率等经营指标
- [ ] **Phase 14: 策略模板库与运营台整合** - 用模板化起步和统一运营台串起前面所有能力

</details>

## Progress

| Phase | Milestone | Requirements | Status |
|-------|-----------|--------------|--------|
| 1. 核心规则执行引擎 | v1.0 | REXEC-01, REXEC-03, REXEC-04, PERF-01, PERF-02, SEC-02 | ✅ Complete |
| 2. 数据持久化与版本管理 | v1.0 | PERS-01, PERS-02, PERS-03, VER-01, VER-02 | ✅ Complete |
| 3. 规则配置界面 | v1.0 | RCONF-01, RCONF-02, UI-01, UI-02, UI-03 | ✅ Complete |
| 4. 监控与安全增强 | v1.0 | MON-01, MON-02, SEC-01 | ✅ Complete |
| 5. 灰度发布与异步执行 | v1.0 | REXEC-02, REXEC-05, VER-03, VER-04 | ✅ Complete |
| 6. 测试验证与分析 | v1.0 | TEST-01, TEST-02, TEST-03, MON-03, MON-04 | ✅ Complete |
| 7. 高级功能与扩展 | v1.0 | ADV-02, ADV-03 | ✅ Complete |
| 8. 权限控制 | v1.0+ | AUTH / RBAC internal extension | ✅ Complete |
| 9. 特征字典与规则兼容底座 | v1.1 | FEAT-01 ~ FEAT-04 | ⏳ Planned |
| 10. 人工审核闭环与标签反馈 | v1.1 | REVIEW-01 ~ REVIEW-04 | ⏳ Planned |
| 11. 历史样本回放与仿真评估 | v1.1 | SIM-01 ~ SIM-04 | ⏳ Planned |
| 12. 发布治理与环境晋级 | v1.1 | RELEASE-01 ~ RELEASE-04 | ⏳ Planned |
| 13. 业务指标分析与复盘看板 | v1.1 | BIZ-01 ~ BIZ-04 | ⏳ Planned |
| 14. 策略模板库与运营台整合 | v1.1 | TPL-01 ~ TPL-03 | ⏳ Planned |

## Phase Details

### Phase 9: 特征字典与规则兼容底座

**Goal**: 建立统一的特征元数据中心，让规则、流程图、模板和未来分析都引用同一套特征定义，同时兼容历史 DSL 中直接写死的字段名。

**Depends on**: Phase 8（权限控制，便于治理 API 和页面授权）

**Requirements**: FEAT-01, FEAT-02, FEAT-03, FEAT-04

**Success Criteria**:
1. 用户可维护特征定义、别名、类型、敏感级别和适用范围
2. 规则表单、流程图节点和模板参数可从特征字典选择字段
3. 规则保存时可提示未知特征和类型不匹配，但不破坏历史规则运行
4. 系统可查看特征被哪些规则、模板或流程节点引用

**Plans**: 3 plans

- Plan 09-01: 特征字典数据模型与治理 API
- Plan 09-02: 规则/流程图编辑器集成与引用校验
- Plan 09-03: 运行时别名解析、引用关系与敏感字段治理

### Phase 10: 人工审核闭环与标签反馈

**Goal**: 把 `MANUAL_REVIEW` 从决策枚举扩展成审核工单、处理记录和结果回写闭环，为误杀率和欺诈率分析提供真值来源。

**Depends on**: Phase 9（需要特征和样本快照定义）

**Requirements**: REVIEW-01, REVIEW-02, REVIEW-03, REVIEW-04

**Success Criteria**:
1. 任意 `MANUAL_REVIEW` 决策都能自动生成工单并带上命中原因与样本快照
2. 审核员可认领、分配、处理、关闭工单并记录结论与证据
3. 审核结果和后续订单真值能回写为反馈标签
4. 队列、SLA、积压和结果分布可被查询和展示

**Plans**: 3 plans

- Plan 10-01: 审核工单数据模型与创建链路
- Plan 10-02: 审核工作台与处理流程
- Plan 10-03: 标签回写、队列指标与权限校验

### Phase 11: 历史样本回放与仿真评估

**Goal**: 建立样本集和批量回放能力，让规则版本在发布前能够先用历史数据预演，并输出差异、风险与收益估算。

**Depends on**: Phase 9, Phase 10

**Requirements**: SIM-01, SIM-02, SIM-03, SIM-04

**Success Criteria**:
1. 用户可从执行日志或业务订单生成样本集
2. 用户可选择基线版本和候选版本发起批量回放
3. 系统能输出命中变化、人审变化和高风险样本明细
4. 系统能基于带标签样本估算误杀风险、欺诈拦截增益和收益影响

**Plans**: 3 plans

- Plan 11-01: 样本集与回放任务模型
- Plan 11-02: 批量重跑执行器与差异明细
- Plan 11-03: 风险/收益估算与回放报告页面

### Phase 12: 发布治理与环境晋级

**Goal**: 把当前“直接发布版本”的能力升级为发布单、审批流、证据包和环境晋级/回滚治理，降低生产变更风险。

**Depends on**: Phase 11（生产发布需要回放/灰度证据）

**Requirements**: RELEASE-01, RELEASE-02, RELEASE-03, RELEASE-04

**Success Criteria**:
1. 用户可为规则或决策流版本创建发布单并填写风险和回滚预案
2. 审批人可按环境审批或驳回发布单
3. 生产发布前必须绑定回放报告、灰度记录或其他证据
4. 系统支持环境晋级、发布记录查询和按发布单回滚

**Plans**: 3 plans

- Plan 12-01: 发布单与审批模型
- Plan 12-02: 发布执行、环境晋级与回滚编排
- Plan 12-03: 发布证据包、差异展示与审计增强

### Phase 13: 业务指标分析与复盘看板

**Goal**: 从“执行量/命中率”升级到“GMV/误杀率/欺诈率/追回金额”的经营视角分析，并与回放、发布、审核数据联动。

**Depends on**: Phase 10, Phase 11, Phase 12

**Requirements**: BIZ-01, BIZ-02, BIZ-03, BIZ-04

**Success Criteria**:
1. 用户可按规则、版本、环境查看 GMV、订单量、通过/拒绝/人审率
2. 用户可查看基于反馈标签计算的欺诈率、误杀率、追回金额和损失金额
3. 用户可按渠道、用户分层、时间区间和发布单切片分析
4. 用户可比较发布前后或基线/候选版本的业务指标变化

**Plans**: 2 plans

- Plan 13-01: 业务事实汇总与指标 API
- Plan 13-02: 复盘看板、对比视图与分层切片

### Phase 14: 策略模板库与运营台整合

**Goal**: 将特征字典、回放、发布建议和业务分析沉淀为可复用模板，并通过统一运营台把规则运营链路串起来。

**Depends on**: Phase 9, Phase 11, Phase 12, Phase 13

**Requirements**: TPL-01, TPL-02, TPL-03

**Success Criteria**:
1. 用户可按场景浏览内置模板并查看依赖特征与适用说明
2. 用户可把模板实例化为规则/流程草稿并带出默认参数
3. 用户可保存团队内部模板，并查看推荐上线方式和监控建议
4. 规则详情页可串联模板、回放、发布和业务分析入口

**Plans**: 2 plans

- Plan 14-01: 模板模型、模板目录与实例化流程
- Plan 14-02: 统一运营台整合与模板复用治理

## Dependencies

```text
Phase 9  特征字典与规则兼容底座
   |
   +--> Phase 10 人工审核闭环与标签反馈
   |        |
   |        +--> Phase 11 历史样本回放与仿真评估
   |                     |
   |                     +--> Phase 12 发布治理与环境晋级
   |                     |
   |                     +--> Phase 13 业务指标分析与复盘看板
   |                                     |
   +-------------------------------------+--> Phase 14 策略模板库与运营台整合
```

## Key Milestones

1. **Phase 9 完成**: 特征引用从“自由文本”升级为“可治理资产”，为后续模板/回放打底
2. **Phase 10 完成**: `MANUAL_REVIEW` 进入可运营、可追踪的工单闭环
3. **Phase 11 完成**: 任何高风险规则变更都能先回放再发布
4. **Phase 12 完成**: 生产发布具备审批、证据和回滚治理
5. **Phase 13 完成**: 规则效果可用业务指标而非纯技术指标评估
6. **Phase 14 完成**: 业务可从模板起步，并通过统一运营台完成闭环操作
