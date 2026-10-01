# Another Rule Engine

> 面向电商反欺诈场景的低代码风控规则引擎，支持可视化规则配置、决策流编排、灰度发布和版本管理。业务人员可独立配置风控规则，**50ms 内返回决策结果**（实测 p50 ≈ 1ms，压测 p95 = 18.3ms）。

> 2026-09 起后端以 Kotlin + 云原生架构重写完成（旧 Java 单体已移除，历史见 tag `legacy/single-machine-java17`），前端 React 应用保留，API 契约保持兼容。

## 功能特性

- **可视化规则编辑器** — 表单化配置条件/动作，无需编写代码；支持切换到 Groovy DSL 模式自由编写脚本
- **决策流编排** — 基于 React Flow 的拖拽式流程编辑器，条件分支、规则集、黑白名单、合并等节点类型
- **版本管理与灰度发布** — 每次修改自动生成版本快照；按流量比例灰度推送，支持可视化 Diff 对比
- **决策服务** — 无状态水平扩展，Groovy 沙箱执行（SecureASTCustomizer 白名单），编译缓存 + Redis pub/sub 即时失效
- **权限与安全** — Sa-Token RBAC、团队数据隔离、审计日志；跨服务会话经 Redis 共享
- **可观测** — Prometheus 指标 + Grafana 面板 + JSON 结构化日志 + K6 压测脚本

## 架构：一个代码库、三个部署物

```
modules/
├── domain/        # 纯 Kotlin 领域模型，零框架依赖
├── dsl/           # 规则 DSL 结构模型（与前端 dslGenerator/dslParser 对应）
├── engine/        # Groovy 脚本引擎：编译缓存、沙箱校验、类加载隔离
├── storage/       # Exposed 表定义 + 仓储 + Flyway 迁移（V1..V26）
├── shared/        # Kafka 事件契约、缓存抽象、通用设施
├── decision-api/  # 部署物 1：决策服务（8081，无状态，HPA 按 QPS 扩缩）
├── admin-api/     # 部署物 2：管理服务（8080，规则 CRUD/版本/灰度/特征目录）
└── log-consumer/  # 部署物 3：日志消费（Kafka → 批量落库，规划中）
build-logic/       # Gradle 约定插件（ruleengine.kotlin-library / ruleengine.spring-app）
frontend/          # React + TypeScript + Vite（3000 端口）
deploy/            # docker-compose + K8s manifests + Prometheus/Grafana/Logstash + K6
```

分布式关键设计：决策节点完全无状态；规则配置以 PostgreSQL 为唯一事实源；编译脚本缓存本地 Caffeine + Redis pub/sub 即时失效 + 短 TTL 兜底；决策请求全程钉住规则版本号保证灰度一致性。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 / 运行时 | Kotlin 2.4 (K2) / JDK 25 (LTS) |
| 应用框架 | Spring Boot 4.1（webmvc starter） |
| 脚本引擎 | Groovy 5（规则 DSL，宿主为 Kotlin） |
| ORM / 数据库 | Exposed 1.x / PostgreSQL 16 |
| 缓存 / 会话 | Redis（Sa-Token 跨服务会话 + 缓存失效广播）、Caffeine |
| 消息（规划） | Kafka（决策日志流，切换点已预留） |
| 构建 / 质量 | Gradle 9.8 (Kotlin DSL) / ktlint / Spotless |

## 快速开始

前置：**JDK 25**（Gradle 必须跑在其上，如 `export JAVA_HOME=~/.sdkman/candidates/java/25.0.1-graalce`）

```bash
./gradlew build                      # 全量构建 + 测试 + 格式检查
./gradlew test                       # 全部测试（413 个，集成测试走 Testcontainers）
./gradlew :modules:admin-api:bootRun     # 管理服务 :8080
./gradlew :modules:decision-api:bootRun  # 决策服务 :8081
cd frontend && npx vite --port 3000      # 前端 :3000（dev 代理指向 8080）
```

本地全栈（含 PostgreSQL/Redis/可观测组件）：

```bash
docker compose -f deploy/compose.local.yml up -d
```

数据源与 Redis 地址经 `DB_URL` / `REDIS_URL` 环境变量覆盖，默认值见各部署物 `application.yml`。首次启动 Flyway 自动执行迁移（V1..V26），初始管理员 `admin / admin123`。

## 文档

- [`AGENTS.md`](AGENTS.md) — 项目上下文唯一权威来源（技术栈矩阵、Boot 4 坑位、路线进度）
- [`pub_docs/`](pub_docs/README.md) — 功能文档与界面截图
- [`.planning/STATE.md`](.planning/STATE.md) — 当前里程碑状态（后续演进基线）

## 许可证

暂未设置开源许可证，默认保留所有权利。
