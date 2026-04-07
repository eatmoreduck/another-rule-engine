# 多环境管理

> 功能状态：**开发中**（默认关闭，需通过配置开关启用）

## 功能概述

支持 DEV / STAGING / PRODUCTION 三套环境，每套环境拥有独立的规则集。支持环境间规则克隆（发布），实现规则从开发→预发布→生产的渐进式上线流程。

## 数据模型

### environments 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(255) | 环境名称（唯一） |
| type | VARCHAR(20) | 环境类型：DEV / STAGING / PRODUCTION |
| description | TEXT | 环境描述 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

默认三条记录由迁移脚本 `V7__create_environments.sql` 初始化。

### 规则与环境的关联

`rules` 表和 `decision_flows` 表均有 `environment_id` 外键字段（由 V7 和 V9 迁移脚本添加），关联到 `environments.id`。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/environments` | 获取所有环境列表 |
| GET | `/api/v1/environments/{id}` | 获取环境详情 |
| GET | `/api/v1/environments/{id}/rules` | 获取环境下的规则 |
| POST | `/api/v1/environments/{from}/clone/{to}` | 克隆环境规则 |

### 克隆规则请求示例

```
POST /api/v1/environments/DEV/clone/STAGING
Content-Type: application/json

{
  "overwrite": true,
  "operator": "admin"
}
```

响应：
```json
{
  "success": true,
  "clonedCount": 12,
  "skippedCount": 3,
  "message": "克隆完成: 12 条已克隆, 3 条已跳过"
}
```

## 前端页面

路由：`/environments`，侧边栏菜单项「多环境」。

页面展示：
- 三张环境卡片（DEV / STAGING / PRODUCTION），显示环境类型、描述、规则数量
- 「克隆到」按钮，弹出克隆配置弹窗（目标环境 + 是否覆盖）

## 已知限制（待完善）

| 限制 | 说明 |
|------|------|
| 无环境切换器 | 没有全局环境下拉框，规则列表页不支持按环境筛选 |
| 无环境 CRUD API | 后端 Service 有 createEnvironment 方法，但 Controller 未暴露 |
| 克隆仅覆盖规则 | DecisionFlow 也支持 environment_id，但克隆操作未包含决策流 |
| 执行未隔离 | 规则执行时未按环境过滤，所有环境的规则混在一起查 |
| 缓存未隔离 | RuleCacheService 缓存 key 不包含环境维度 |

## 配置开关

在 `application.yml` 中：

```yaml
rule-engine:
  features:
    multi-environment:
      enabled: false   # 设为 true 启用多环境功能
```

前端侧边栏菜单项会根据此配置自动隐藏/显示。
