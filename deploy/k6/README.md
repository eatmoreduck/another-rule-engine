# K6 决策链路压测（阶段 6）

验证 50ms 决策 SLA：`decision-load.js` 双层场景压测决策服务。

## 场景设计

| 场景 | 端点 | 含义 | 阈值 |
|------|------|------|------|
| `cold-decide` | `POST /api/v1/decide` | 直传 Groovy 脚本，每次冷编译执行（沙箱编译开销在路径上） | p95 < 250ms（上界参考，可用 `COLD_P95_MS` 调整） |
| `warm-decide` | `POST /api/v1/decide/{ruleKey}` | cache-aware 决策：灰度分流 + 快照缓存命中 | **p95 < 50ms（SLA）**，可用 `WARM_P95_MS` 调整 |

公共阈值：HTTP 错误率 < 0.1%（`ERROR_RATE`）；warm 场景错开 30s 启动，避免冷启动 JIT 干扰。

> 注：SLA 阈值挂在自定义指标 `warm_e2e_ms` / `cold_e2e_ms` 上（端到端耗时，含
> 网络/序列化）；服务端纯执行耗时看 `warm_server_exec_ms` / `cold_server_exec_ms`
> （取自响应体 `executionTimeMs`），两口径对照可分离压测机与网络的影响。

## 运行前置

### 1. 起 compose 栈

```bash
docker compose -f deploy/compose.local.yml up -d --build
```

decision-api 在 compose 中已设 `SA_TOKEN_AUTH_ENABLED=false`（决策侧免认证），压测无需 token。

### 2.（warm 场景需要）准备规则数据

warm 场景按 `RULE_KEY`（默认 `k6-warm-rule`）走 cache-aware 决策，规则需真实存在。
任选其一：

**方式 A —— 管理服务建规则（推荐）**：登录种子账号（V16 迁移内置
`admin/admin123`，SUPER_ADMIN）后调管理 API：

```bash
TOKEN=$(curl -s -X POST http://localhost:18080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

# 创建规则（ruleKey 与 k6 的 RULE_KEY 一致，默认 k6-warm-rule；创建即 v1 生效）
curl -s -X POST http://localhost:18080/api/v1/rules \
  -H "Authorization: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"ruleKey":"k6-warm-rule","ruleName":"K6 压测规则","groovyScript":"return features.order_amount > 1000 ? '\''REJECT'\'' : '\''PASS'\''"}'
```

> 规则不存在时 warm 场景不会报错：决策服务 fail-safe 返回 200 + `REJECT`（原因
> "规则不存在或未启用"），但那不是真实 SLA 数据——压测前务必先建规则。

**方式 B —— 无规则数据时的行为**：warm 场景对不存在的 ruleKey 会拿到
200 + REJECT（fail-safe），脚本不报错但数据不是真实 SLA，仅可用于链路连通性观察；
要只跑 cold 场景，临时注释 `options.scenarios['warm-decide']` 即可。

### 3. 认证说明

- **关闭认证（compose 默认）**：decision-api 以 `SA_TOKEN_AUTH_ENABLED=false` 启动，`TOKEN` 不传即可。
- **开启认证时**：token 从 admin-api 登录获取。注意拆分部署下两服务 Sa-Token 会话
  独立（阶段 5 接入 Redis 会话后才跨服务共享），登录得到的 token 需能被 decision-api
  校验——同一套 `sys_*` 权限数据下，走 Redis 会话或对 decision-api 单独登录均可：

```bash
TOKEN=$(curl -s -X POST http://localhost:18081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<密码>"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
```

## 运行压测

**宿主机 k6（需本机安装 k6：`brew install k6`）：**

```bash
k6 run deploy/k6/decision-load.js   # 默认 BASE_URL=http://localhost:18081
```

**容器方式（无需本机安装，走 compose 内网）：**

```bash
docker compose -f deploy/compose.local.yml --profile loadtest run --rm k6 \
  run /scripts/decision-load.js
# 传规则 key / token：
RULE_KEY=k6-warm-rule TOKEN=xxx \
  docker compose -f deploy/compose.local.yml --profile loadtest run --rm k6 \
  run /scripts/decision-load.js
```

**调档：**

```bash
COLD_VUS=10 WARM_VUS=50 DURATION=5m k6 run deploy/k6/decision-load.js
```

## 结果解读

- `warm_e2e_ms p(95)` < 50ms → SLA 达标（端到端口径；thresholds 与摘要都以该自定义指标为准）
- `warm_server_exec_ms p(95)` → 服务端纯执行（阶段 3 基线 p50≈1ms）
- `decision_reject_rate`：REJECT 占比（业务拒绝，非错误）
- `decision_timeout_total`：命中 `timeout` 标记的决策次数（>0 需排查执行预算）
- 完整指标同步看 Grafana（http://localhost:13001，仪表盘「规则引擎 · 决策链路总览」）

> 已知 k6 行为：内建 `http_req_duration` 按 scenario tag 拆出的子指标在
> `handleSummary` 中序列化为 0（实测 grafana/k6 latest），故端到端分位数改用
> 自定义 Trend（`warm_e2e_ms` / `cold_e2e_ms`）承载，thresholds 同样挂其上。
