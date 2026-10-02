# AGENTS.md

本文件是本仓库统一的 AI 编码助手上下文（Claude Code / Codex 等工具均读取此文件）。
由原 CLAUDE.md 与旧版 AGENTS.md（描述已废弃的 Java 单体，内容见 tag legacy/single-machine-java17）合并而来。

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

## 架构：一个代码库、两个部署物（2026-10 合并部署物）

```
modules/
├── domain/        # 纯 Kotlin 领域模型，零框架依赖
├── dsl/           # 规则 DSL 结构模型（与前端 dslGenerator/dslParser 对应）
├── engine/        # Groovy 脚本引擎：编译缓存、沙箱校验、类加载隔离
├── storage/       # Exposed 表定义 + 仓储
├── shared/        # Kafka 事件契约、缓存抽象、通用设施
├── decision-api/  # 决策链路组件库（spring-library 约定）：决策执行/特征解析/灰度路由，
│                  #   经 classpath 由 admin-api 根包扫描吸入同一上下文（8080）
├── admin-api/     # 部署物：统一后端（8080，管理面 + 决策面，Sa-Token 认证）
└── log-consumer/  # 部署物 2（延后）：日志消费（8082，Kafka → 批量落库）

build-logic/      # Gradle 约定插件（kotlin-library / spring-library / spring-app）
frontend/         # React + TypeScript + Vite（保留，端口 3000，代理统一后端 8080）
deploy/           # docker-compose（本地联调）+ k8s manifests（阶段 6）
```

> **2026-10 合并说明**：原 decision-api（决策服务）与 admin-api（管理服务）双部署物合并为
> 单一后端（本地开发只起一个 JVM）。决策链路仍保持无状态设计与 Redis pub/sub 失效广播，
> 将来数据面/管控面需要物理隔离时，为 decision-api 加回启动类与配置即可拆回。
> 合并时处理的 bean 冲突：双侧 EngineConfiguration（校验链 5s vs 决策热路径 200ms 双引擎，
> bean 名 `groovyScriptEngine` / `decisionScriptEngine` 以 @Qualifier 区分）、RedisConfiguration
> （决策侧为超集，保留）、StpInterface（决策侧复用 admin 的 AuthRepository/StpInterfaceImpl）。

分布式关键设计：决策节点完全无状态；规则配置以 PostgreSQL 为唯一事实源；编译脚本缓存本地 Caffeine + Redis pub/sub 即时失效 + 短 TTL 兜底；Kafka 不做实例级广播（consumer group 只投递一份），实例级失效广播走 Redis pub/sub；决策请求全程钉住规则版本号保证灰度一致性。

## 构建与运行

**Gradle 必须跑在 JDK 25 上**（本机 SDKMAN 路径，shell 里 `java` 默认是 17，需显式指定）：

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.1-graalce

./gradlew build                 # 全量构建 + 测试 + 格式检查
./gradlew spotlessApply         # 自动格式化（提交前必跑）
./gradlew test                  # 全部测试
./gradlew :modules:engine:test  # 单模块测试
./gradlew :modules:admin-api:bootRun   # 启动统一后端（管理面 + 决策面，8080）
```

前端：`cd frontend && npx vite --port 3000`

## 代码约定

- Kotlin 官方代码风格，ktlint 强制执行（CI 挡板）
- 库模块约定插件 `ruleengine.kotlin-library`、Spring 组件库插件 `ruleengine.spring-library`（all-open）、Boot 应用约定插件 `ruleengine.spring-app`（build-logic）
- 依赖版本一律进 `gradle/libs.versions.toml`，不在模块里写死版本号；Boot 相关依赖版本由 BOM 管理
- 领域模型优先 data class + 不可变；对外 DTO 与领域模型分开放
- 注释中文，类名/方法名/变量名英文
- 测试：库模块用 kotlin.test，Boot 模块用 spring-boot-starter-test；方法名用反引号英文短句

## 实施路线（当前进度见 .planning/STATE.md）

| 阶段 | 内容 | 状态 |
|------|------|------|
| 0 | 脚手架：多模块 + JDK 25 + 约定插件 + 版本矩阵核实 | ✅ |
| 1 | domain + dsl + engine：领域模型 / DSL 树 / 沙箱脚本引擎（208 测试） | ✅ |
| 2a | storage：Exposed 表映射 + 仓储 + Flyway 基线（44 测试；2026-10 重启后收敛为单一 V1__init） | ✅ |
| 2b | admin-api 核心：认证 + 规则 + 版本 + 灰度 + 特征目录（53 测试，契约对齐旧 API） | ✅ |
| 2c | admin-api 外围：决策流管理、黑白名单、审计查询、环境管理 | ✅ |
| 3 | decision-api：决策链路 + 协程并发取特征 + 灰度分流（p50≈1ms，SLA 余量 50 倍） | ✅ |
| 4 | 最小化：JSON 日志（Logstash 采集）；log-consumer 延后、Kafka 仅留 ExecutionLogBuffer.flush 切换点（原 V26 回填随 V1__init 收敛移除） | ✅ |
| 5 | Redis：Sa-Token 跨服务会话（断级降级+熔断）+ 缓存失效广播（AFTER_COMMIT 发布） | ✅ |
| 6 | deploy/：镜像 + K8s manifests + compose 全栈 + Prometheus/Grafana/Logstash + K6（端到端 warm p95=18.3ms） | ✅ |
| 7 | 合并部署物：decision-api 组件并入 admin-api 单进程（双脚本引擎/单 StpInterface/单 RedisConfiguration），单端口 8080 | ✅ |

### 收尾尾巴（后续批次候选）
- log-consumer + Kafka 日志流（切换点已预留）/ ES 日志检索
- ImportExport、TestExecution、Cache/System/Analytics/Metrics 接口、审计写入侧（AOP 埋点）
- DSL JSON 载荷的决策执行（当前 `{` 开头的表单 DSL 在决策侧 fail-safe REJECT，主流数据面是 Groovy 文本）
- K8s 集群级验证（当前仅 client 端结构自检）、prometheus-adapter/KEDA 按决策 QPS 扩缩
- 环境隔离的 schema 级方案（rule_key 全局唯一约束使环境克隆形同跳过）

### Boot 4 关键坑位记录（新部署物必读）
- **webmvc starter 不含事务自动装配**：部署物必须显式依赖 `org.springframework.boot:spring-boot-transaction`，否则 `@Transactional` 静默失效（Exposed 报 "No transaction in context"）
- **Exposed 1.x 用 v1 包名**（`org.jetbrains.exposed.v1.*`）；`spring-transaction 1.5.0` 与 Spring 7 实测兼容；**禁用** `exposed-spring-boot-starter`（绑 Boot 3.5.8）
- **Sa-Token** 用 `sa-token-spring-boot4-starter:1.46.0`（官方 Boot 4 支持）；上下文由 Servlet Filter 建立，MockMvc 测试需显式 `addFilters`
- **Jackson 3**：`tools.jackson` 命名空间（非 2.x com.fasterxml），ObjectMapper 不可变、builder 构造
- **Testcontainers 2.x** 改名：`testcontainers-postgresql` / `testcontainers-junit-jupiter`（旧名 2.0.5 下无 artifact）

## 开发注意事项

- **旧代码处置**：旧 Java 单体已于 2026-10-01 整体删除（commit 5e1cefb2 移除 src/ 共 219 文件，根目录旧单体 Dockerfile/docker-compose.yml 同批清理，Java 代码零残留）；回滚靠 tag `legacy/single-machine-java17` 或 git revert
- **预编译脚本插件限制**：build-logic 内不能用 `libs.*` 访问器，需经 `VersionCatalogsExtension` 程序化读取；业务模块不受影响
- **Boot 4 starter**：web MVC 用 `spring-boot-starter-webmvc`（旧名 `web` 仍存在但已非推荐）
- 版本核实方法：任何新依赖先查 `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml`，不凭记忆写版本号
- 规则执行必须过沙箱（SecureASTCustomizer 白名单），AI 生成的规则同样要过静态分析
- **UI 走查截图/临时产物**：Playwright 截图等临时文件统一落 `.playwright-mcp/`（已被 ignore），**禁止在仓库根目录或源码目录落任何图片/临时文件**（.gitignore 已有根目录锚定规则 `/*.png` 兜底）；走查截图用完即弃不进仓库，产品截图只进 `pub_docs/screenshots/`
- **UI 自动化走查**：改动涉及页面/接口时，除 API E2E（`scripts/e2e_full_test.py` + `scripts/e2e_node_matrix_test.py`）外，用 Playwright 过一遍受影响页面（含动态实体详情页与表单弹窗），检测崩溃与 4xx/5xx

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
