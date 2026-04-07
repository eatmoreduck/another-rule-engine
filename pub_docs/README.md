# 灰度发布 - 决策流可视化图形对比

## 功能说明
灰度发布中的决策流版本对比，使用两侧 React Flow 画布并排展示，用颜色高亮标识新增/删除/修改的节点和边，让业务人员一眼看出两个版本的差异。

## 截图索引

| 文件 | 说明 |
|------|------|
| 01-grayscale-list.png | 灰度列表页面 |
| 02-create-modal-default.png | 新建灰度弹窗（默认规则类型） |
| 03-create-modal-decision-flow-selected.png | 选择"决策流"目标类型 |
| 04-flow-selected.png | 选择具体决策流后加载版本选项 |
| 05-version-selected-with-meta.png | 选择版本号后显示元信息对比 |
| 10-rule-create-modal-tabs.png | 规则类型：规则逻辑对比 + Groovy 代码对比（2 个 Tab） |
| 11-df-visual-diff-create.png | 决策流类型：可视化对比 Tab（两侧 React Flow 画布） |
| 12-df-json-diff-create.png | 决策流类型：JSON 对比 Tab（文本 diff 备选） |
| 13-list-diff-visual.png | 灰度列表中：点击眼睛图标 → 可视化对比弹窗 |
| 14-list-diff-json.png | 灰度列表中：点击眼睛图标 → JSON 对比弹窗 |

## 测试结果

| 测试项 | 预期 | 实际 | 结果 |
|--------|------|------|------|
| 规则类型 Tab 数量 | 2 个（规则逻辑对比、Groovy 代码对比） | ["规则逻辑对比","Groovy 代码对比"] | PASS |
| 决策流类型 Tab 数量 | 2 个（可视化对比、JSON 对比） | ["可视化对比","JSON 对比"] | PASS |
| 可视化画布节点渲染 | nodes > 0 | nodes=10（双侧合计） | PASS |
| 可视化画布边渲染 | edges > 0 | edges=8（双侧合计） | PASS |
| 列表对比弹窗 - 可视化 Tab | 正确渲染 | 正常渲染 | PASS |
| 列表对比弹窗 - JSON Tab | 正确渲染 | 正常渲染 | PASS |

## 颜色图例
- 绿色：新增节点/边
- 红色：删除节点/边（+ 半透明）
- 橙色：修改节点/边
- 默认：未变更

## 修改文件
- `frontend/src/components/FlowGraphDiff.tsx` — 新建，可视化图形对比核心组件
- `frontend/src/pages/GrayscalePage.tsx` — 集成可视化对比 Tab
