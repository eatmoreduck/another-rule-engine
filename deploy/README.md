# 部署与可观测（阶段 6）

Kotlin 重写后的容器化部署物：后端统一镜像（admin-api：管理面+决策面，2026-10 合并部署物）、
前端镜像、本地 compose 全栈、K8s manifests、Prometheus/Grafana/Logstash 可观测栈、K6 压测脚本。

```
deploy/
├── docker/                  # 两个 Dockerfile + 前端 nginx 配置
│   ├── admin-api.Dockerfile       # 统一后端（8080：管理面 + 决策面）
│   ├── frontend.Dockerfile        # React 静态站点（nginx 分流）
│   └── nginx-frontend.conf        # API 统一上游 + SPA 回退 + gzip
├── k8s/                     # K8s manifests（apply 顺序 = 文件序号）
├── compose.local.yml        # 本地全栈（含可观测）
├── observability/
│   ├── prometheus/prometheus.yml
│   ├── grafana/provisioning/      # 数据源 + 仪表盘自动装载
│   └── logstash/pipeline/logstash.conf
├── k6/                      # 50ms SLA 压测（见 k6/README.md）
└── README.md                # 本文件
```

## 与旧根目录 Dockerfile / docker-compose.yml 的关系

**旧文件属 legacy 单体（Java 17 monolith，tag `legacy/single-machine-java17`），已弃用。**
根目录 `Dockerfile`、`docker-compose.yml` 仅服务旧 `src/` 单体构建，与 Kotlin 多模块
（modules/decision-api、modules/admin-api）无关；阶段 6 起一律使用 `deploy/` 下的新体系。
旧文件暂留工作树供旧版回溯，随旧代码整体删除时一并清理。

## 镜像构建

在**仓库根目录**执行（context 必须是仓库根，Dockerfile 在 deploy/docker/ 下）：

```bash
docker build -f deploy/docker/admin-api.Dockerfile    -t rule-engine/admin-api:dev .
docker build -f deploy/docker/frontend.Dockerfile     -t rule-engine/frontend:dev .
```

要点（构建实测记录）：

| 项 | 结论 |
|----|------|
| 构建阶段基础镜像 | `gradle:9-jdk25`（官方多架构，Ubuntu noble 系，amd64/arm64 已核实，非 alpine） |
| 运行阶段基础镜像 | `eclipse-temurin:25-jre-jammy`（jammy，多架构含 arm64） |
| ARM64 教训 | eclipse-temurin/gradle 的 **alpine 变体无 arm64**，维护者本机 ARM64 构建必须用 Debian/Ubuntu 系 |
| Boot 4 分层提取 | `java -Djarmode=tools -jar app.jar extract --layers --destination extracted`（官方 Boot 4.1 文档命令，`--launcher` 不需要）；产出 `dependencies/`、`spring-boot-loader/`、`snapshot-dependencies/`、`application/`（内含瘦启动 jar + 业务 jar）四个独立镜像层，代码变更只重建 application 层 |
| 启动命令 | `java -jar app.jar`（extract 保留源 jar 名，Dockerfile 先 `cp` 成固定名 `app.jar` 再解包） |
| JVM 参数 | 经 `JVM_OPTS` 注入，默认 `-XX:+UseCompactObjectHeaders -XX:+UseZGC -XX:MaxRAMPercentage=75`（容器内实测 ZGC 初始化正常） |
| 非 root | 运行用户 `app`（uid 999），`USER app` |
| 健康检查 | `curl /actuator/health/readiness`（temurin jammy **无 wget/curl**，镜像内已装 curl） |
| 依赖缓存 | BuildKit cache mount 挂 `/root/.gradle`，重构建不重复下载依赖，缓存不进镜像层 |
| 镜像大小 | decision-api / admin-api ≈ 371MB（含阶段 5 Redis 集成）；frontend ≈ 183MB（本机实测，2026-09-30） |

⚠️ **前端镜像构建当前被前端源码阻塞**：`frontend/src/components/FlowGraphDiff.tsx`
的 `computeDiff` 函数头部整块丢失、`src/types/rule.ts` 的 `RuleSelectOption.version`
重复声明（均已在 HEAD 提交，属历史合并事故）。修复落地前，frontend 镜像需用临时
修正上下文构建（阶段 6 验证即用此方式，镜像本身可用）：
```bash
# 示例：把仓库拷到临时目录、修正上述两处后，context 指向临时目录构建
docker build -f deploy/docker/frontend.Dockerfile -t rule-engine/frontend:dev /tmp/fe-verify
```

## 本地全栈（compose）

```bash
# 起栈（首次会构建三个镜像 + 拉取可观测镜像）
docker compose -f deploy/compose.local.yml up -d --build

# 就绪后访问
#   前端           http://localhost:13000        （nginx 统一 API 上游）
#   admin-api      http://localhost:18080        （统一部署物：管理面 + 决策面）
#   Grafana        http://localhost:13001        （admin/admin）
#   Prometheus     http://localhost:13002
#   PostgreSQL     localhost:15432               （yare/yare_secret）
#   Redis          localhost:16379

# 看日志（JSON 日志走 stdout + 共享卷）
docker compose -f deploy/compose.local.yml logs -f admin-api
docker compose -f deploy/compose.local.yml logs logstash   # 已解析的 JSON 事件

# 压测（见 deploy/k6/README.md）
docker compose -f deploy/compose.local.yml --profile loadtest run --rm k6 run /scripts/decision-load.js

# 销毁
docker compose -f deploy/compose.local.yml down -v
```

端口全部避开宿主机已占用的 5432/8080（映射见文件头注释）。
栈拓扑：

```
宿主机 13000 ──> frontend(nginx:80)
                   ├── /api/*                ──> admin-api:8080（统一后端）
                   └── /                     ──> SPA 静态文件
admin-api:8080    ──> postgres:5432 / redis:6379（阶段 5 生效）
prometheus:9090   ──> 抓 admin-api /actuator/prometheus
grafana:3000      ──> 读 prometheus（数据源/仪表盘自动装载）
logstash          ──> 读共享卷 app-logs(/app/logs ←→ /logs) 的 JSON 滚动日志 → stdout
```

统一后端以 `SPRING_PROFILES_ACTIVE=json` 运行：stdout 出 Logstash JSON
（容器日志路径），同时写 `/app/logs/*.json` 滚动文件（共享卷 → Logstash 采集路径）。

## K8s 部署

`deploy/k8s/` 按**文件序号**顺序 apply：

```bash
# 1. 先填真实凭据：02-secrets.yaml 里所有 CHANGE_ME（或改用 external-secrets）
vi deploy/k8s/02-secrets.yaml

# 2. 镜像推送到集群可达 registry，并替换 manifests 里的 image 地址
#    （当前为本地 tag rule-engine/*:dev，仅供 kind/minikube 本地集群 load）

# 3. 依次 apply
kubectl apply -f deploy/k8s/00-namespace.yaml
kubectl apply -f deploy/k8s/01-configmaps.yaml
kubectl apply -f deploy/k8s/02-secrets.yaml
kubectl apply -f deploy/k8s/10-deployment-admin-api.yaml
kubectl apply -f deploy/k8s/12-deployment-frontend.yaml
kubectl apply -f deploy/k8s/20-services.yaml
kubectl apply -f deploy/k8s/30-ingress.yaml
```

清单要点：

| 资源 | 关键配置 |
|------|---------|
| Ingress | ingress-nginx：`/api` → admin-api（统一后端）；`/` → frontend。其他 Ingress Controller 需等价改写 |
| 探针 | `/actuator/health/liveness`、`/actuator/health/readiness`（Boot probes，ConfigMap 已显式开启） |
| 资源 | admin-api（统一后端）：req 250m/512Mi，limit 1/1Gi（MaxRAMPercentage=75 → 堆约 768Mi） |
| 日志 | 每副本 emptyDir 挂 `/app/logs`（节点侧 DaemonSet 采集），stdout 走集群日志栈 |
| frontend | nginx:stable master 以 root 绑 80；集群强制 non-root 时换 `nginxinc/nginx-unprivileged`（8080）并调 Service targetPort |

语法校验：本机 kubectl（v1.33.9）存在但无可达集群（OrbStack K8s 未启用），
`apply --dry-run=client` 需要服务端 OpenAPI/RESTMapper，故阶段 6 采用
Python YAML 解析 + 结构自检替代（namespace/引用/selector/probes/端口/HPA 目标
交叉校验全部通过）。集群就位后执行：

```bash
kubectl apply --dry-run=client -f deploy/k8s/*.yaml
```

## 可观测

- **Prometheus**：`deploy/observability/prometheus/prometheus.yml`，抓 admin-api:8080
  与自身，15s 间隔。K8s 下建议换 kube-prometheus-stack + ServiceMonitor。
- **Grafana**：provisioning 自动装载数据源与仪表盘「规则引擎 · 决策链路总览」，
  面板覆盖：决策 QPS、决策延迟 p50/p95/avg（SLA 红线 50ms）、decision.errors、
  HTTP 请求量/延迟、JVM 堆内存、GC、进程 CPU。指标名与运行中服务
  `/actuator/prometheus` 实测一致（`decision_duration_seconds`、`decision_errors_total`、
  `http_server_requests_seconds_*`、`jvm_memory_used_bytes` 等）。
  注意：分位数面板依赖 `MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_DECISION_DURATION`
  环境变量（compose/K8s 配置已内置）；HPA 多副本下 p95 为各副本最大值，
  跨副本聚合口径用 avg 面板。
- **Logstash**：`file input + json codec` 读共享卷日志 → stdout（ES 输出留有注释占位）。
  容器内置了 256m 堆，仅文件采集足够。

## 遗留风险与注意事项（给最终集成）

1. **前端源码两处缺陷**（阻塞 frontend 镜像干净构建，见上文镜像构建节）：
   `FlowGraphDiff.tsx` computeDiff 缺块、`rule.ts` version 重复。需前端侧修复后重推。
2. **Sa-Token 会话**：阶段 5 的 Redis 会话已在 compose 栈实测通过——登录颁发的
   token 全服务有效（Redis 共享），伪造 token 401。compose 经 `REDIS_URL=redis://redis:6379`
   注入连接；K8s 侧在 02-secrets.yaml 填真实 REDIS_URL 即可获得同等行为。
   压测场景可用 `SA_TOKEN_AUTH_ENABLED=false` 省去 token 管理（合并后该开关覆盖
   管理面+决策面，压测栈勿暴露公网）。
3. **decision.duration 分位数**：多副本下 Prometheus 无法跨副本聚合 quantile，
   SLA 判定建议用 K6（端到端）+ avg 面板（服务端）；或后续改 histogram 桶
   （需代码/配置层把 summary 换成 percentiles-histogram）。
4. **首次起栈顺序**：PostgreSQL 健康检查通过后服务才启动；统一后端首次启动
   跑 Flyway 初始化脚本（V1__init），通常数十秒，健康检查 start-period 已放宽。
5. **镜像 tag**：manifests 里是本地 dev tag，上集群前改 registry 地址并建议用不可变
   digest 或版本 tag，别用 :latest/:dev。
6. **前端镜像 healthcheck 用 curl**：nginx:stable 镜像只有 curl 没有 wget；
   若改用其他 nginx 基础镜像（尤其 alpine 系），同步检查 HEALTHCHECK 的可用命令。
7. **拆回双部署物**：决策链路组件保留在独立模块 `modules/decision-api`
   （spring-library 约定，all-open 编译）；需要数据面/管控面物理隔离时，为其
   加回启动类（收窄扫描范围）与 application.yml 即可拆回，E2E/网关路由同步还原。

## 阶段 6 已实测通过的关键链路（compose 栈）

- 三个镜像构建 + 容器启动（非 root、ZGC/压缩对象头、分层 jar、curl 健康检查）
- decision-api 全链路：Flyway 迁移 → `/api/v1/decide` 直传决策（首编 396ms → 复用 3ms）
- K6 双场景压测（3147 请求，0 失败 0 超时）：warm 端到端 p95 18.29ms < 50ms SLA ✓
- admin-api 登录（种子账号 admin/admin123）→ 建规则 → decision 按 key 决策
- 跨服务会话（阶段 5）：admin 登录 token 打认证开启的 decision 实例 200 / 伪造 401
- Prometheus 三目标 UP，decision_duration p50/p95 分位序列存在（env 注入生效）
- Grafana 仪表盘 provisioning + 数据源查询 200；Logstash 解析共享卷 JSON 日志（84+ 事件）
- frontend nginx 分流：decide 族/decision-flows execute → decision；其余 /api → admin；SPA 回退 OK
