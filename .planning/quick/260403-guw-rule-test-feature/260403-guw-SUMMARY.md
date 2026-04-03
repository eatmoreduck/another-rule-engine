# Phase quick Plan 01: Rule Test Feature Summary

---
phase: quick
plan: 01
subsystem: frontend
tags: [testing, modal, rule-execution, ux]
dependency_graph:
  requires: [dslParser, analytics-api, antd]
  provides: [RuleTestModal, extractFieldNames, in-page-test-button]
  affects: [RuleDetailPage, RuleEditPage]
tech_stack:
  added: []
  patterns: [recursive-tree-walk, auto-json-from-script]
key_files:
  created:
    - frontend/src/components/rules/RuleTestModal.tsx
  modified:
    - frontend/src/utils/dslParser.ts
    - frontend/src/pages/RuleDetailPage.tsx
    - frontend/src/pages/RuleEditPage.tsx
decisions:
  - "EditPage uses generatedScript (current edits) rather than saved groovyScript for testing"
  - "Default field values set to -1 for all extracted fields"
metrics:
  duration: 226s
  completed: "2026-04-03"
  tasks: 2
  files: 4
---

**One-liner:** 规则详情页和编辑页新增内联测试功能，自动从 Groovy 脚本提取字段名生成默认 JSON 参数，调用后端测试接口并回显决策结果。

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | 添加 extractFieldNames 并创建 RuleTestModal | 355771df | dslParser.ts, RuleTestModal.tsx |
| 2 | 集成测试按钮到 RuleDetailPage 和 RuleEditPage | c1c17d01 | RuleDetailPage.tsx, RuleEditPage.tsx |

## Key Changes

### Task 1: extractFieldNames + RuleTestModal

- `extractFieldNames(node)` 递归遍历 ConditionTreeNode，提取所有非空 fieldName 并去重返回字符串数组
- `RuleTestModal` 组件接收 `open`, `onClose`, `ruleKey`, `groovyScript` 四个 props
- Modal 打开时自动解析 groovyScript，提取字段名生成 `{ fieldName: -1 }` 默认 JSON
- "运行"按钮调用 `executeTest(ruleKey, parsedJson)` 执行测试
- 成功时显示决策 Alert (PASS=success, REJECT=warning) + Descriptions 详情
- 失败时显示 error Alert 含 errorMessage
- matchedConditions 用 Tag 列表展示

### Task 2: 页面集成

- **RuleDetailPage**: 顶部操作区 "编辑" 按钮前新增 "测试" 按钮，点击弹出 RuleTestModal 传入 rule.ruleKey 和 rule.groovyScript
- **RuleEditPage**: 顶部 "保存" 按钮前新增 "测试" 按钮 (disabled={isNew})，使用 generatedScript 传入当前编辑中的脚本（含未保存修改）

## Verification

- TypeScript 编译: `npx tsc --noEmit` -- PASSED (0 errors)
- Vite 构建: `npx vite build` -- PASSED (built in 3.59s)

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check: PASSED

- [x] frontend/src/components/rules/RuleTestModal.tsx exists
- [x] frontend/src/utils/dslParser.ts contains extractFieldNames
- [x] frontend/src/pages/RuleDetailPage.tsx references RuleTestModal
- [x] frontend/src/pages/RuleEditPage.tsx references RuleTestModal
- [x] Commit 355771df exists
- [x] Commit c1c17d01 exists
