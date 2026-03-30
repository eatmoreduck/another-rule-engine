# Phase 3: 规则配置界面 - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

## Phase Boundary

提供可视化和表单两种配置方式，让业务人员能够独立配置规则。包括：前端 React 应用、规则表单配置、可视化决策流编辑器、Groovy DSL 自动生成、规则管理界面（列表/详情/编辑/预览）。不包含 AI 辅助规则生成（后续阶段）、监控仪表盘（Phase 4）、灰度发布界面（Phase 5）。

## Implementation Decisions

### 前端技术栈
- **D-01:** 使用 React 19 + TypeScript + Vite 构建前端应用
- **D-02:** 使用 Ant Design 5.x 作为 UI 组件库
- **D-03:** 使用 React Flow 11.x 实现可视化决策流编辑器
- **D-04:** 前后端分离架构，前端独立部署（与后端 Spring Boot 分开）
- **D-05:** 使用 Zustand 或 React Context 管理前端状态

### 配置交互方式
- **D-06:** 提供**双模式**配置：简单规则使用条件-动作表单，复杂决策流使用可视化流程图
- **D-07:** **条件-动作表单模式**：用户选择字段名、比较符、阈值、动作（通过/拒绝/人工审核），系统自动拼装成 Groovy if-else 脚本
- **D-08:** **可视化流程图模式**：拖拽条件节点、连线、配置属性。支持顺序、分支、循环等流程控制
- **D-09:** 两种模式最终都生成 Groovy DSL 脚本，统一通过后端引擎执行
- **D-10:** 业务人员不直接接触 Groovy 代码，但开发人员可查看/编辑生成的脚本

### 页面布局与导航
- **D-11:** 使用顶部导航布局（非左侧边栏）
- **D-12:** 主要页面：规则列表、规则详情/编辑、决策流编辑器、测试验证
- **D-13:** 顶部导航包含：Logo、规则管理、测试验证、系统设置（可选）

### Groovy DSL 自动生成
- **D-14:** 实现 DSL 生成器，将表单配置转换为 Groovy 脚本
- **D-15:** 实现 DSL 生成器，将可视化流程图转换为 Groovy 脚本
- **D-16:** 支持预览生成的 Groovy 脚本（供开发人员调试）
- **D-17:** 生成的脚本需通过后端沙箱安全检查

### Claude's Discretion
- 前端状态管理方案（Zustand vs Context）
- CSS 方案（CSS Modules / Tailwind / styled-components）
- 前端路由方案
- API 请求库（axios / fetch）
- 前端测试框架（Vitest + React Testing Library）
- DSL 生成器的具体语法设计
- 可视化节点的具体类型和属性

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 项目规范
- `.planning/PROJECT.md` — 项目愿景、核心价值、技术栈约束
- `.planning/REQUIREMENTS.md` — v1 需求定义，Phase 3 对应需求 RCONF-01, RCONF-02, UI-01, UI-02, UI-03
- `.planning/ROADMAP.md` — Phase 3 详细定义、成功标准、依赖关系

### 技术栈规范
- `.planning/research/STACK.md` — 推荐技术栈、版本选择（React 19, Ant Design 5.x, React Flow 11.x）

### 后端 API
- `.planning/phases/01-core-engine/01-CONTEXT.md` — Phase 1 API 设计决策（D-16 到 D-19）
- `.planning/phases/02-persistence-versioning/02-04-rule-lifecycle.md` — Phase 2 规则管理 API

## Existing Code Insights

### Reusable Assets
- 后端已有完整的 REST API：POST /api/v1/decide, GET/POST/PUT/DELETE /api/v1/rules
- 后端已有脚本验证 API：POST /api/v1/rules/validate
- 后端已有版本管理 API

### Established Patterns
- Spring Boot 3.3 后端，JSON 请求/响应
- 规则状态：DRAFT/ACTIVE/ARCHIVED/DELETED
- 特征获取三级策略已实现

### Integration Points
- 前端通过 REST API 与后端交互
- 规则保存 API 已就绪
- 脚本验证 API 已就绪
- 版本管理 API 已就绪

## Specific Ideas

1. **表单配置示例：** 用户选择"金额 > 1000 → 拒绝"，系统生成 `if (features.amount > 1000) { return 'REJECT' }`
2. **流程图配置示例：** 用户拖拽一个"黑名单检查"节点 → 一个"风险评估"节点 → 一个"决策"节点，连线后配置每个节点的参数
3. **核心价值验证：** 配置界面是否足够简单，让不懂代码的业务人员能在 5 分钟内创建一条规则

## Deferred Ideas

### Phase 4 内容
- 规则执行监控仪表盘
- 沙箱安全增强界面

### Phase 5 内容
- 灰度发布配置界面
- 流量分配可视化

### Phase 6 内容
- 规则测试工具界面
- 冲突检测结果展示

### 后续版本
- AI 辅助规则生成（对话式）
- 决策表模式（Excel 风格）
- 规则模板市场

---

*Phase: 03-rule-config-ui*
*Context gathered: 2026-03-30*
