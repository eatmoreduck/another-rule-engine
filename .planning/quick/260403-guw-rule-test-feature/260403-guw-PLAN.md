---
phase: quick
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - frontend/src/utils/dslParser.ts
  - frontend/src/components/rules/RuleTestModal.tsx
  - frontend/src/pages/RuleDetailPage.tsx
  - frontend/src/pages/RuleEditPage.tsx
autonomous: true
must_haves:
  truths:
    - "用户在规则详情页和编辑页都能看到测试按钮"
    - "点击测试按钮弹出 Modal，显示从规则脚本自动提取的默认 JSON 参数"
    - "用户可以修改 JSON 参数并点击运行，调用后端接口执行规则"
    - "执行结果（决策、原因、耗时）回显在 Modal 中"
  artifacts:
    - path: "frontend/src/components/rules/RuleTestModal.tsx"
      provides: "规则测试 Modal 组件"
    - path: "frontend/src/utils/dslParser.ts"
      provides: "新增 extractFieldNames 工具函数"
  key_links:
    - from: "frontend/src/components/rules/RuleTestModal.tsx"
      to: "/api/v1/test/rules/{ruleKey}/execute"
      via: "executeTest API 函数"
      pattern: "executeTest\\(ruleKey"
    - from: "frontend/src/components/rules/RuleTestModal.tsx"
      to: "frontend/src/utils/dslParser.ts"
      via: "extractFieldNames 函数提取字段名"
      pattern: "extractFieldNames"
    - from: "frontend/src/pages/RuleDetailPage.tsx"
      to: "frontend/src/components/rules/RuleTestModal.tsx"
      via: "Modal 组件引用"
    - from: "frontend/src/pages/RuleEditPage.tsx"
      to: "frontend/src/components/rules/RuleTestModal.tsx"
      via: "Modal 组件引用"
---

<objective>
在规则详情页和编辑页添加"测试"功能，用户点击按钮弹出 Modal，自动从 Groovy 脚本中提取字段名生成默认 JSON 参数（空值填 -1），运行后调用已有的后端测试接口并回显结果。

Purpose: 让用户在配置/查看规则时能快速验证规则逻辑是否正确，无需跳转到独立的测试页面。
Output: RuleTestModal 组件，集成到 RuleDetailPage 和 RuleEditPage
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@frontend/src/types/analytics.ts
@frontend/src/types/ruleConfig.ts
@frontend/src/api/analytics.ts
@frontend/src/utils/dslParser.ts
@frontend/src/utils/dslGenerator.ts
@frontend/src/pages/RuleDetailPage.tsx
@frontend/src/pages/RuleEditPage.tsx

<interfaces>
<!-- 已有 API 函数 — executor 直接使用，无需探索 -->

From frontend/src/api/analytics.ts:
```typescript
export async function executeTest(
  ruleKey: string,
  testData: Record<string, unknown>,
): Promise<TestResult>;
```

From frontend/src/types/analytics.ts:
```typescript
export interface TestResult {
  ruleKey: string;
  decision: string;
  reason: string;
  executionTimeMs: number;
  success: boolean;
  errorMessage?: string;
  matchedConditions: string[];
  executionContext?: Record<string, unknown>;
}
```

From frontend/src/types/ruleConfig.ts:
```typescript
export type ConditionTreeNode = ConditionNode | LogicGroup;

export interface ConditionNode {
  id: string;
  type: 'condition';
  fieldName: string;
  operator: Operator;
  threshold: string | number;
}

export interface LogicGroup {
  id: string;
  type: 'group';
  logic: 'AND' | 'OR';
  children: ConditionTreeNode[];
}
```

From frontend/src/utils/dslParser.ts:
```typescript
export function parseGroovyToSingleRule(script: string): SingleRuleConfig;
// SingleRuleConfig.condition 是 ConditionTreeNode 类型
```

From frontend/src/types/rule.ts:
```typescript
export interface Rule {
  ruleKey: string;
  groovyScript: string;
  // ... 其他字段
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: 添加 extractFieldNames 工具函数并创建 RuleTestModal 组件</name>
  <files>frontend/src/utils/dslParser.ts, frontend/src/components/rules/RuleTestModal.tsx</files>
  <action>
1. 在 `frontend/src/utils/dslParser.ts` 文件末尾新增导出函数 `extractFieldNames`:

```typescript
/**
 * 从 ConditionTreeNode 递归提取所有字段名（去重）
 */
export function extractFieldNames(node: ConditionTreeNode): string[] {
  const names = new Set<string>();

  function walk(n: ConditionTreeNode) {
    if (n.type === 'condition') {
      if (n.fieldName && n.fieldName.trim()) {
        names.add(n.fieldName.trim());
      }
    } else if (n.type === 'group') {
      n.children.forEach(walk);
    }
  }

  walk(node);
  return Array.from(names);
}
```

2. 创建 `frontend/src/components/rules/RuleTestModal.tsx` 组件:

Props 接口:
```typescript
interface RuleTestModalProps {
  open: boolean;
  onClose: () => void;
  ruleKey: string;
  groovyScript: string;
}
```

组件逻辑:
- 导入 `parseGroovyToSingleRule`, `extractFieldNames` from `../utils/dslParser`
- 导入 `executeTest` from `../api/analytics`
- 导入 `TestResult` from `../types/analytics`
- 当 Modal 打开时（open 变为 true），使用 `parseGroovyToSingleRule(groovyScript)` 解析脚本得到 SingleRuleConfig，然后用 `extractFieldNames(config.condition)` 提取字段名，构建默认 JSON 对象: 所有字段值设为 `-1`
- 默认 JSON 用 `JSON.stringify(defaultJson, null, 2)` 格式化显示在 Input.TextArea 中
- 使用 Ant Design 的 Modal, Input.TextArea, Button, Alert, Descriptions, Tag, Spin 组件
- "运行"按钮调用 `executeTest(ruleKey, JSON.parse(textAreaValue))`
- 结果区域显示:
  - 成功时: Alert 显示决策结果（PASS=success 色, REJECT=warning 色）+ Descriptions 显示 ruleKey, 决策, 耗时, 状态
  - 失败时: Alert type="error" 显示错误信息 errorMessage
  - matchedConditions 用 Tag 列表展示
- Modal 宽度 640px，标题 "测试规则"
- TextArea 行数 8，用等宽字体
- 运行中显示 loading 状态（按钮 loading + 内容区 Spin）
- JSON 解析失败时用 message.error 提示，不发送请求
  </action>
  <verify>
    <automated>cd /Users/leon/Documents/Justforfun/another-rule-engine/frontend && npx tsc --noEmit --pretty 2>&1 | head -50</automated>
  </verify>
  <done>extractFieldNames 函数可正确从条件树提取字段名；RuleTestModal 组件接收 ruleKey + groovyScript，自动生成默认 JSON，可执行测试并显示结果；TypeScript 编译无错误</done>
</task>

<task type="auto">
  <name>Task 2: 在 RuleDetailPage 和 RuleEditPage 集成测试按钮和 Modal</name>
  <files>frontend/src/pages/RuleDetailPage.tsx, frontend/src/pages/RuleEditPage.tsx</files>
  <action>
1. 修改 `frontend/src/pages/RuleDetailPage.tsx`:

- 新增导入: `import RuleTestModal from '../components/rules/RuleTestModal';`
- 新增导入: `import { ThunderboltOutlined } from '@ant-design/icons';`
- 新增 state: `const [testModalOpen, setTestModalOpen] = useState(false);`
- 在页面顶部操作按钮区域（`<Space>` 中 "编辑" 按钮前面）添加:
  ```tsx
  <Button
    icon={<ThunderboltOutlined />}
    onClick={() => setTestModalOpen(true)}
  >
    测试
  </Button>
  ```
- 在页面底部 `</div>` 前添加:
  ```tsx
  <RuleTestModal
    open={testModalOpen}
    onClose={() => setTestModalOpen(false)}
    ruleKey={rule.ruleKey}
    groovyScript={rule.groovyScript}
  />
  ```
  注意此组件需要在 `if (!rule)` 判断之后放置，确保 rule 不为 null。

2. 修改 `frontend/src/pages/RuleEditPage.tsx`:

- 新增导入: `import RuleTestModal from '../components/rules/RuleTestModal';`
- 新增导入: `import { ThunderboltOutlined } from '@ant-design/icons';`
- 新增 state: `const [testModalOpen, setTestModalOpen] = useState(false);`
- 在页面顶部操作按钮区域（`<div className="page-header-actions">` 中 "保存" 按钮前面）添加:
  ```tsx
  <Button
    icon={<ThunderboltOutlined />}
    onClick={() => setTestModalOpen(true)}
    disabled={isNew}
  >
    测试
  </Button>
  ```
  新建规则时禁用测试按钮（还没有 ruleKey），已有规则编辑时可用。
- 在页面底部 `</div>` 前添加（仅在非新建时渲染）:
  ```tsx
  {!isNew && ruleKey && existingRule && (
    <RuleTestModal
      open={testModalOpen}
      onClose={() => setTestModalOpen(false)}
      ruleKey={ruleKey}
      groovyScript={generatedScript}
    />
  )}
  ```
  注意这里用 `generatedScript` 而非 `existingRule.groovyScript`，这样测试的是当前编辑中的脚本（含未保存修改），更符合用户预期。
  </action>
  <verify>
    <automated>cd /Users/leon/Documents/Justforfun/another-rule-engine/frontend && npx tsc --noEmit --pretty 2>&1 | head -50</automated>
  </verify>
  <done>RuleDetailPage 顶部操作区有"测试"按钮，点击弹出 RuleTestModal；RuleEditPage 顶部操作区有"测试"按钮（新建时禁用），点击弹出 Modal 测试当前编辑中的脚本；TypeScript 编译无错误</done>
</task>

</tasks>

<verification>
1. TypeScript 编译通过: `cd frontend && npx tsc --noEmit`
2. 前端构建通过: `cd frontend && npx vite build`
</verification>

<success_criteria>
- RuleDetailPage 和 RuleEditPage 均有可见的"测试"按钮
- 点击测试按钮弹出 Modal，TextArea 中自动填入从 Groovy 脚本提取的字段名，值为 -1
- 用户可修改 JSON，点击"运行"调用 POST /api/v1/test/rules/{ruleKey}/execute
- 测试结果（决策、原因、耗时）回显在 Modal 内
</success_criteria>

<output>
After completion, create `.planning/quick/260403-guw-rule-test-feature/260403-guw-SUMMARY.md`
</output>
