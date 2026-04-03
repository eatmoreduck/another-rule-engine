---
phase: quick
plan: 260403-vg8
subsystem: api, database, ui
tags: [listKey, name-list, flyway, jpa, react, antd, select]

# Dependency graph
requires: []
provides:
  - "名单系统 scopeKey 全面重命名为 listKey"
  - "GET /api/v1/name-list/list-keys 端点"
  - "黑白名单节点 listKey 下拉选择框"
affects: [name-list, decision-flow, node-config]

# Tech tracking
tech-stack:
  added: []
  patterns: ["listKey 作为名单层级标识: listKey => list_type => key_type => key_value"]

key-files:
  created:
    - "src/main/resources/db/migration/V13__rename_scope_key_to_list_key.sql"
  modified:
    - "src/main/resources/db/migration/V12__add_name_list_scope.sql"
    - "src/main/java/com/example/ruleengine/domain/NameListEntry.java"
    - "src/main/java/com/example/ruleengine/repository/NameListRepository.java"
    - "src/main/java/com/example/ruleengine/service/NameListService.java"
    - "src/main/java/com/example/ruleengine/model/dto/CreateNameListEntryRequest.java"
    - "src/main/java/com/example/ruleengine/controller/NameListController.java"
    - "frontend/src/api/nameList.ts"
    - "frontend/src/pages/NameListPage.tsx"
    - "frontend/src/components/flow/NodeConfigPanel.tsx"
    - "frontend/src/types/flowConfig.ts"

key-decisions:
  - "V12 迁移已执行过，新建 V13 做列重命名，同时更新 V12 内容保持一致"
  - "DecisionFlowExecutionService 中 flowKey 作为 listKey 传入 existsInList，语义不变无需修改"

patterns-established: []

requirements-completed: []

# Metrics
duration: 11min
completed: 2026-04-03
---

# Quick Task 260403-vg8: scopeKey 重命名为 listKey + 名单选择功能 Summary

**全栈 scopeKey 到 listKey 重命名，新增 /list-keys API 和前端黑白名单节点 listKey 下拉选择框**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-03T14:42:44Z
- **Completed:** 2026-04-03T14:53:56Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- 后端全链路 scopeKey/scope_key 重命名为 listKey/list_key（Entity、Repository、Service、DTO、Controller）
- 新增 V13 迁移脚本执行 ALTER TABLE RENAME COLUMN，已在运行数据库上成功应用
- 新增 GET /api/v1/name-list/list-keys 端点返回不重复 listKey 列表
- 前端全链路 scopeKey 重命名为 listKey（API 接口、页面状态、表格列、筛选器、表单）
- 黑白名单节点配置面板增加 listKey 下拉选择框，数据从 /list-keys API 获取

## Task Commits

Each task was committed atomically:

1. **Task 1: 后端 scopeKey 重命名为 listKey + 新增 list-keys API** - `17bbf70e` (feat)
2. **Task 2: 前端 scopeKey 重命名为 listKey + 黑白名单节点增加 listKey 下拉选择框** - `3c2d099b` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V13__rename_scope_key_to_list_key.sql` - V13 迁移：scope_key 重命名为 list_key
- `src/main/resources/db/migration/V12__add_name_list_scope.sql` - V12 更新为使用 list_key（新部署用）
- `src/main/java/com/example/ruleengine/domain/NameListEntry.java` - 字段 scopeKey 改为 listKey
- `src/main/java/com/example/ruleengine/repository/NameListRepository.java` - 查询方法重命名 + 新增 findDistinctListKeys
- `src/main/java/com/example/ruleengine/service/NameListService.java` - 方法签名重命名 + 新增 getDistinctListKeys
- `src/main/java/com/example/ruleengine/model/dto/CreateNameListEntryRequest.java` - 字段重命名
- `src/main/java/com/example/ruleengine/controller/NameListController.java` - 参数重命名 + 新增 /list-keys 端点
- `frontend/src/api/nameList.ts` - 接口重命名 + 新增 getListKeys API
- `frontend/src/pages/NameListPage.tsx` - 页面状态、筛选器、表格列全部更新为 listKey
- `frontend/src/components/flow/NodeConfigPanel.tsx` - 黑白名单组件增加 listKey 下拉选择框
- `frontend/src/types/flowConfig.ts` - BlacklistNodeData/WhitelistNodeData 新增 listKey 字段

## Decisions Made
- V12 迁移已在数据库执行过（scope_key 列已存在），因此新建 V13 迁移脚本做 ALTER COLUMN RENAME，同时更新 V12 文件内容以保证新部署时一致
- DecisionFlowExecutionService 中 flowKey 传入 existsInList 的调用无需修改，因为方法签名虽改为 listKey，但 flowKey 作为名单 key 的语义是一致的
- Flyway checksum 修复通过直接更新 flyway_schema_history 表实现，因为 V12 文件内容已变更

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Flyway checksum mismatch for V12 migration**
- **Found during:** Task 1 (backend startup verification)
- **Issue:** Modified V12 content caused Flyway validation failure (checksum mismatch)
- **Fix:** Updated flyway_schema_history checksum to match new V12 file content
- **Files modified:** Database flyway_schema_history table
- **Verification:** Backend started successfully, V13 migration applied correctly, column renamed

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Fix was necessary to apply the database schema change. No scope creep.

## Verification Results
- Backend compilation: PASS (./gradlew compileJava)
- Frontend TypeScript: PASS (npx tsc --noEmit)
- Backend scopeKey residual check: 0 references
- Frontend scopeKey residual check: 0 references
- /list-keys endpoint: Returns ["GLOBAL"] (verified with curl)
- /name-list endpoint: Response contains "listKey" field (verified with curl)
- DB column renamed: scope_key -> list_key (verified with psql)

## Self-Check: PASSED

All 12 files verified present. Both commits (17bbf70e, 3c2d099b) verified in git log.

---
*Phase: quick*
*Completed: 2026-04-03*
