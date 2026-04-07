# 规则引擎 - 产品文档

## 功能模块

| 模块 | 文档 | 状态 |
|------|------|------|
| 灰度发布 - 可视化对比 | [screenshots/README.md](screenshots/README.md) | 已上线 |
| 多环境管理 | [multi-environment.md](multi-environment.md) | 开发中（默认关闭） |
| 导入导出 | [import-export.md](import-export.md) | 开发中（默认关闭） |

## 功能开关配置

在 `src/main/resources/application.yml` 中控制：

```yaml
rule-engine:
  features:
    multi-environment:
      enabled: false   # 多环境管理，设为 true 启用
    import-export:
      enabled: false   # 规则导入导出，设为 true 启用
```

后端 Controller / Service 通过 `@ConditionalOnProperty` 条件加载，前端通过 `/api/v1/features` API 动态隐藏菜单。

## 目录结构

```
pub_docs/
├── README.md                    # 本文件（总目录）
├── multi-environment.md         # 多环境管理文档
├── import-export.md             # 导入导出文档
└── screenshots/                 # 测试截图
    ├── README.md                # 灰度可视化对比截图说明
    ├── 01-grayscale-list.png
    ├── 02-create-modal-default.png
    └── ...
```
