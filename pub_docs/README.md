# 规则引擎 - 产品文档

## 功能模块

| 模块 | 文档 | 状态 |
|------|------|------|
| 灰度发布 - 可视化对比 | [screenshots/README.md](screenshots/README.md) | 已上线 |
| 多环境管理 | [multi-environment.md](multi-environment.md) | 开发中（默认关闭） |
| 导入导出 | [import-export.md](import-export.md) | 开发中（默认关闭） |

## 功能开关配置

> ⚠️ 以下功能开关属旧 Java 单体实现（`src/main/resources/application.yml`，已删除）。
> Kotlin 新后端尚未重实现这两个能力（见 `AGENTS.md` 收尾尾巴清单），当前配置不生效，
> 本节仅作为功能设计参考保留。

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
