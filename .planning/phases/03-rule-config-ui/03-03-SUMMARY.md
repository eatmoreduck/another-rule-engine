# Phase 3 Plan 03: 可视化流程图编辑器 (React Flow) - 执行总结

**

**执行日期:** 2026-03-30
**

**状态:** 已完成
**|

**核心目标:**
实现可视化决策流编辑器，让用户通过拖拽节点和连线来配置复杂规则流程，并自动转换为 Groovy DSL 脚本。

**

**完成内容:**

## 任务 1: 流程图类型定义和 DSL 生成器 ✅

- `frontend/src/types/flowConfig.ts` - 流程图节点类型定义（FlowNode, FlowEdge, ConditionEdgeData 等）
- `frontend/src/utils/flowDslGenerator.ts` - 流程图转 Groovy DSL 生成器（支持 true/false 分支递归遍历）
- `frontend/src/utils/dslGenerator.ts` - 表单 DSL 生成器（复用公共函数）

 `generateGroovyFromFlow(nodes, edges) => string` 生成完整 Groovy 脚本：
  ```groovy
  def evaluate(Map features) {
    if (features.amount > 1000) {
      return [decision: 'REJECT', reason: '金额超过阈值']
    }
    if (features.riskScore >= 80) {
      return [decision: 'MANUAL_REVIEW', reason: '高风险评分']
    }
    return [decision: 'PASS', reason: '默认通过']
  }
  ```

## 任务 2: 可视化流程图编辑器页面和组件 ✅

- `frontend/src/components/flow/nodes/StartNode.tsx` - 开始节点（绿色+Handle）
- `frontend/src/components/flow/nodes/EndNode.tsx` - 结束节点（红色+Handle）
- `frontend/src/components/flow/nodes/ConditionNode.tsx` - 条件节点（蓝色+双分支Handle: true/false）
- `frontend/src/components/flow/nodes/ActionNode.tsx` - 决策节点（颜色编码 PASS/REJECT/MANUAL_REVIEW）
- `frontend/src/components/flow/NodePalette.tsx` - 节点拖拽面板（可拖拽到画布添加节点）
- `frontend/src/components/flow/NodeConfigPanel.tsx` - 节点属性配置面板（双击节点编辑属性）
- `frontend/src/components/flow/FlowCanvas.tsx` - React Flow 画布组件（集成拖拽+连线+自定义节点）
- `frontend/src/pages/FlowEditorPage.tsx` - 可视化编辑页面（ReactFlowProvider包裹+三栏布局）

## 路由配置 ✅
- `/rules/new/flow` -> FlowEditorPage（新建模式）
- `/rules/:ruleKey/edit/flow` -> FlowEditorPage（编辑模式）
- RuleListPage 的 Dropdown 菜单包含"可视化创建"入口

 ModeSwitch 攌持表单/流程图切换

## 关键技术决策

- 使用 `@xyflow/react` v12.10.2（React Flow 12 最新版）
- ReactFlowProvider 包裹整个编辑器，useReactFlow 提供拖拽 API
- 条件节点支持 true/false 双分支（两个 source Handle）
- useNodesState/useEdgesState 管理节点和边状态
- useBlocker 实现离开页面未保存确认
底部脚本预览面板实时显示生成的 Groovy DSL
保存时调用后端 API 验证并保存规则

