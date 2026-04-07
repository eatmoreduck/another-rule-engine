# 规则导入导出

> 功能状态：**开发中**（默认关闭，需通过配置开关启用）

## 功能概述

支持将规则（含版本历史）导出为 JSON 文件，以及从 JSON 文件导入规则。适用于规则备份、跨系统迁移、批量配置等场景。

## 导出格式

```json
{
  "formatVersion": "1.0",
  "exportedAt": "2026-04-08T10:30:00",
  "exportedBy": "admin",
  "rules": [
    {
      "rule": {
        "ruleKey": "test-rule-001",
        "ruleName": "测试规则",
        "ruleDescription": "用于测试的示例规则",
        "groovyScript": "def amount = input.amount...",
        "version": 3,
        "enabled": true,
        "environmentId": null
      },
      "versions": [
        {
          "version": 3,
          "groovyScript": "...",
          "changeReason": "修改阈值",
          "changedBy": "admin"
        },
        {
          "version": 2,
          "groovyScript": "...",
          "changeReason": "新增条件",
          "changedBy": "admin"
        }
      ]
    }
  ]
}
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/export/rules` | 导出所有规则 |
| GET | `/api/v1/export/rules/{ruleKey}` | 导出单条规则 |
| POST | `/api/v1/export/rules/batch` | 批量导出（body: ruleKey 列表） |
| POST | `/api/v1/import/rules` | 导入规则（body: RuleExportData JSON） |

### 导入响应示例

```json
{
  "success": true,
  "importedCount": 8,
  "skippedCount": 2,
  "failedCount": 0,
  "failures": [],
  "message": "导入完成: 8 条成功, 2 条跳过, 0 条失败"
}
```

## 前端页面

路由：`/import-export`，侧边栏菜单项「导入导出」。

页面分两个区域：

### 导出区域
- 「导出全部规则」按钮：一键导出所有规则
- 单条导出：输入 ruleKey 导出指定规则
- 批量导出：多行输入多个 ruleKey，批量导出

### 导入区域
- 拖拽上传 JSON 文件
- 解析后显示预览表格（规则 Key、名称、状态、版本数量）
- 确认导入按钮
- 导入结果统计（成功/跳过/失败数量 + 失败详情）

## 导入规则

- **跳过已存在**：如果目标系统已有相同 ruleKey 的规则，默认跳过
- **保留版本历史**：导入时会同时写入版本记录
- **不覆盖**：暂不支持覆盖模式，已有规则不会被更新

## 已知限制（待完善）

| 限制 | 说明 |
|------|------|
| 仅支持规则 | 不支持决策流的导入导出 |
| 无覆盖模式 | 已存在的规则只能跳过，不能更新 |
| 无环境定向 | 导入时保留源环境的 environmentId，无法指定目标环境 |
| 不含关联数据 | 导出内容不包含灰度配置、名单数据、审计日志 |

## 配置开关

在 `application.yml` 中：

```yaml
rule-engine:
  features:
    import-export:
      enabled: false   # 设为 true 启用导入导出功能
```

前端侧边栏菜单项会根据此配置自动隐藏/显示。
