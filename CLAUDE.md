# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**低代码风控规则引擎**（电商反欺诈场景）：业务人员通过可视化界面配置规则，系统在 50ms 内返回决策结果。核心价值：业务人员可独立配置风控规则，无需开发介入。

> **2026-09 起后端正在推倒重写**：旧 Java 17 单机版已打 tag `legacy/single-machine-java17`（可随时 checkout 回溯），master 上重写为 Kotlin + JDK 25 + 云原生分布式架构。前端 React 应用保留不动，API 契约保持兼容。

## 技术栈（2026-09 经官方仓库 metadata 核实）

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin (K2) | 2.4.20 |
| 运行时 | JDK (LTS) | 25.0.1-graalce（SDKMAN 安装） |
| 构建 | Gradle (Kotlin DSL) | 9.8.0 |
| 应用框架 | Spring Boot | 4.1.1（starter 用 `webmvc` 新命名） |
| 脚本引擎 | Groovy（规则 DSL，宿主为 Kotlin） | 5.1.3 |
| ORM | Exposed | 1.5.0（阶段 2 启用；⚠️ 与 Boot 4.1 兼容性待运行时验证，兜底方案是手动集成 SpringTransactionManager） |
| 数据库 | PostgreSQL | 16 |
| 消息 | Kafka | 阶段 4 引入（决策日志流 + 规则发布事件） |
| 缓存/会话 | Redis + Caffeine | 阶段 5 引入（pub/sub 缓存失效广播、Sa-Token 会话、ShedLock） |
| 格式化 | Spotless + ktlint | 8.9.0 / 1.8.0 |

## 架构：一个代码库、三个部署物

```
modules/
├── domain/        # 纯 Kotlin 领域模型，零框架依赖
├── dsl/           # 规则 DSL 结构模型（与前端 dslGenerator/dslParser 对应）
├── engine/        # Groovy 脚本引擎：编译缓存、沙箱校验、类加载隔离
├── storage/       # Exposed 表定义 + 仓储
├── shared/        # Kafka 事件契约、缓存抽象、通用设施
├── decision-api/  # 部署物 1：决策服务（8080，无状态，HPA 按 QPS 扩缩）
├── admin-api/     # 部署物 2：管理服务（8081，规则 CRUD/版本/灰度/AI/特征目录）
└── log-consumer/  # 部署物 3：日志消费（8082，Kafka → 批量落库）

build-logic/      # Gradle 约定插件（ruleengine.kotlin-library / ruleengine.spring-app）
frontend/         # React + TypeScript + Vite（保留，端口 3000）
deploy/           # docker-compose（本地联调）+ k8s manifests（阶段 6）
```

分布式关键设计：决策节点完全无状态；规则配置以 PostgreSQL 为唯一事实源；编译脚本缓存本地 Caffeine + Redis pub/sub 即时失效 + 短 TTL 兜底；Kafka 不做实例级广播（consumer group 只投递一份），实例级失效广播走 Redis pub/sub；决策请求全程钉住规则版本号保证灰度一致性。

## 构建与运行

**Gradle 必须跑在 JDK 25 上**（本机 SDKMAN 路径，shell 里 `java` 默认是 17，需显式指定）：

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.1-graalce

./gradlew build                 # 全量构建 + 测试 + 格式检查
./gradlew spotlessApply         # 自动格式化（提交前必跑）
./gradlew test                  # 全部测试
./gradlew :modules:engine:test  # 单模块测试
./gradlew :modules:decision-api:bootRun   # 启动决策服务
./gradlew :modules:admin-api:bootRun      # 启动管理服务
```

前端：`cd frontend && npx vite --port 3000`

## 代码约定

- Kotlin 官方代码风格，ktlint 强制执行（CI 挡板）
- 库模块约定插件 `ruleengine.kotlin-library`、Boot 应用约定插件 `ruleengine.spring-app`（build-logic）
- 依赖版本一律进 `gradle/libs.versions.toml`，不在模块里写死版本号；Boot 相关依赖版本由 BOM 管理
- 领域模型优先 data class + 不可变；对外 DTO 与领域模型分开放
- 注释中文，类名/方法名/变量名英文
- 测试：库模块用 kotlin.test，Boot 模块用 spring-boot-starter-test；方法名用反引号英文短句

## 实施路线（当前进度见 .planning/STATE.md）

| 阶段 | 内容 | 状态 |
|------|------|------|
| 0 | 脚手架：多模块 + JDK 25 + 约定插件 + 版本矩阵核实 | ✅ |
| 1 | domain + dsl + engine：领域模型 / DSL 树 / 沙箱脚本引擎（208 测试） | ✅ |
| 2a | storage：Exposed 表映射 + 仓储 + Flyway 基线（44 测试） | ✅ |
| 2b | admin-api 核心：认证 + 规则 + 版本 + 灰度 + 特征目录（53 测试，契约对齐旧 API） | ✅ |
| 2c | admin-api 外围：决策流、黑白名单、导入导出、审计、分析等 | |
| 3 | decision-api：决策链路 + 协程并发取特征 + 灰度分流 | |
| 4 | Kafka + log-consumer + 旧数据回填迁移 | |
| 5 | Redis：会话/缓存失效广播/分布式锁 | |
| 6 | K8s 部署 + 可观测 + 压测（50ms SLA） | |

### Boot 4 关键坑位记录（新部署物必读）
- **webmvc starter 不含事务自动装配**：部署物必须显式依赖 `org.springframework.boot:spring-boot-transaction`，否则 `@Transactional` 静默失效（Exposed 报 "No transaction in context"）
- **Exposed 1.x 用 v1 包名**（`org.jetbrains.exposed.v1.*`）；`spring-transaction 1.5.0` 与 Spring 7 实测兼容；**禁用** `exposed-spring-boot-starter`（绑 Boot 3.5.8）
- **Sa-Token** 用 `sa-token-spring-boot4-starter:1.46.0`（官方 Boot 4 支持）；上下文由 Servlet Filter 建立，MockMvc 测试需显式 `addFilters`
- **Jackson 3**：`tools.jackson` 命名空间（非 2.x com.fasterxml），ObjectMapper 不可变、builder 构造
- **Testcontainers 2.x** 改名：`testcontainers-postgresql` / `testcontainers-junit-jupiter`（旧名 2.0.5 下无 artifact）

## 开发注意事项

- **旧代码处置**：`src/main/java/`（旧 Java 后端）保留在工作树但已不参与构建（root 无 java 插件），新引擎跑通决策链路后整体删除；回滚靠 tag `legacy/single-machine-java17`
- **预编译脚本插件限制**：build-logic 内不能用 `libs.*` 访问器，需经 `VersionCatalogsExtension` 程序化读取；业务模块不受影响
- **Boot 4 starter**：web MVC 用 `spring-boot-starter-webmvc`（旧名 `web` 仍存在但已非推荐）
- 版本核实方法：任何新依赖先查 `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml`，不凭记忆写版本号
- 规则执行必须过沙箱（SecureASTCustomizer 白名单），AI 生成的规则同样要过静态分析

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
