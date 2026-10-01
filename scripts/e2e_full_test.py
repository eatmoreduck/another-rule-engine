#!/usr/bin/env python3
"""端到端全链路自动化测试：特征 → 规则 → 决策流 → 监控 + RBAC 权限矩阵。

用法：
    python3 scripts/e2e_full_test.py

前置：
    - admin-api(8080) / decision-api(8081) 运行中
    - PostgreSQL 可达（脚本会直插/清理 viewer 测试用户）
    - 初始管理员 admin/admin123
"""
import json
import sys
import urllib.error
import urllib.request

BASE_ADMIN = "http://localhost:8080"
BASE_DECISION = "http://localhost:8081"
PG = ["-h", "192.168.5.200", "-U", "yare_app", "-d", "yare_engine"]
PSQL_PASSWORD = "ServBay.dev"

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  ✓ " if ok else "  ✗ ") + name + (f"  [{detail}]" if detail and not ok else ""))


def call(method, url, token=None, body=None, timeout=15):
    """返回 (status_code, parsed_json_or_none)"""
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return e.code, None


def psql(sql):
    import subprocess
    cmd = ["/opt/homebrew/opt/postgresql@18/bin/psql"] + PG + ["-t", "-A", "-c", sql]
    env = {"PGPASSWORD": PSQL_PASSWORD, "PATH": "/usr/bin:/bin:/opt/homebrew/bin"}
    return subprocess.run(cmd, capture_output=True, text=True, env=env).stdout.strip()


# ═══════════════ 场景 A：特征 → 规则 → 决策流 → 监控 全链路 ═══════════════
def scenario_full_chain():
    print("\n▶ 场景 A：特征 → 规则 → 决策流 → 监控 全链路")
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/auth/login",
                     body={"username": "admin", "password": "admin123"})
    token = r.get("token") if status == 200 else None
    check("A1 admin 登录", status == 200 and token, f"status={status}")
    H = token

    # A2 特征目录：登记交易金额特征
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/features/catalog", H, body={
        "code": "txn_amount", "name": "交易金额", "dataType": "NUMBER",
        "sourceType": "REQUEST", "exampleValue": "12000", "description": "E2E 测试特征",
    })
    check("A2 创建特征 txn_amount", status == 200, f"status={status} body={r}")

    # A3 创建规则：大额拒绝
    script = ("def evaluate(Map features) {\n"
              "  if (features.txn_amount > 10000) {\n"
              "    return [decision: 'REJECT', reason: '大额拒绝']\n"
              "  }\n"
              "  return [decision: 'PASS', reason: '金额正常']\n"
              "}")
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/rules", H, body={
        "ruleKey": "e2e_big_amount", "ruleName": "E2E大额规则", "groovyScript": script,
    })
    check("A3 创建规则 e2e_big_amount", status == 200, f"status={status} body={r}")

    # A4 启用规则
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules/e2e_big_amount/enable", H)
    check("A4 启用规则", status == 200, f"status={status}")

    # A5 规则测试接口双分支
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/test/rules/e2e_big_amount/execute", H,
                     body={"txn_amount": 20000})
    check("A5a 测试 20000 → REJECT", status == 200 and r.get("decision") == "REJECT",
          f"status={status} got={r.get('decision')}")
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/test/rules/e2e_big_amount/execute", H,
                     body={"txn_amount": 500})
    check("A5b 测试 500 → PASS", status == 200 and r.get("decision") == "PASS",
          f"status={status} got={r.get('decision')}")

    # A6 按 ruleKey 决策（服务端加载脚本；script 传空串满足 @NotNull）
    status, r = call("POST", f"{BASE_DECISION}/api/v1/decide", H, body={
        "ruleId": "e2e_big_amount", "script": "", "features": {"txn_amount": 20000}})
    check("A6 按 ruleKey 决策 20000 → REJECT", status == 200 and r.get("decision") == "REJECT",
          f"status={status} got={r.get('decision')} body={r}")

    # A7 创建决策流：START → RULESET(引用规则) → END
    graph = {
        "nodes": [
            {"id": "n_start", "type": "start",
             "data": {"label": "开始", "nodeType": "start"}},
            {"id": "n_ruleset", "type": "ruleset",
             "data": {"label": "规则集", "nodeType": "ruleset", "ruleKeys": ["e2e_big_amount"]}},
            {"id": "n_end", "type": "end",
             "data": {"label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "流程通过"}},
        ],
        "edges": [
            {"id": "e1", "source": "n_start", "target": "n_ruleset"},
            {"id": "e2", "source": "n_ruleset", "target": "n_end"},
        ],
    }
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows", H, body={
        "flowKey": "e2e_flow", "flowName": "E2E决策流", "flowDescription": "全链路测试流",
        "flowGraph": json.dumps(graph),
    })
    check("A7 创建决策流 e2e_flow", status == 200, f"status={status} body={r}")

    # A8 启用决策流
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows/e2e_flow/enable", H)
    check("A8 启用决策流", status == 200, f"status={status}")

    # A9 执行决策流双分支（注意：流执行端点的请求体是裸特征 Map，无包装字段）
    status, r = call("POST", f"{BASE_DECISION}/api/v1/decision-flows/e2e_flow/execute", H,
                     body={"txn_amount": 20000})
    check("A9a 执行流 20000 → REJECT", status == 200 and r.get("decision") == "REJECT",
          f"status={status} got={r.get('decision')} body={json.dumps(r, ensure_ascii=False)[:160]}")
    status, r = call("POST", f"{BASE_DECISION}/api/v1/decision-flows/e2e_flow/execute", H,
                     body={"txn_amount": 500})
    check("A9b 执行流 500 → PASS", status == 200 and r.get("decision") == "PASS",
          f"status={status} got={r.get('decision')}")

    # A10 监控数据回流
    status, r = call("GET", f"{BASE_ADMIN}/api/v1/metrics/overview", H)
    total = r.get("totalExecutions", 0) if isinstance(r, dict) else 0
    check("A10 监控 overview 含执行数据", status == 200 and total > 0,
          f"status={status} totalExecutions={total}")
    status, r = call("GET", f"{BASE_ADMIN}/api/v1/logs/recent?limit=5", H)
    check("A11 最近执行日志非空", status == 200 and isinstance(r, list) and len(r) > 0,
          f"status={status} len={len(r) if isinstance(r, list) else r}")

    # A12 名单管理冒烟
    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/name-list", H)
    check("A12 名单列表可读", status == 200, f"status={status}")
    return token


# ═══════════════ 场景 B：RBAC 权限矩阵 ═══════════════
def scenario_rbac(admin_token):
    print("\n▶ 场景 B：RBAC 权限矩阵")
    # B1 未登录 → 401
    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/rules")
    check("B1 无 token → 401", status == 401, f"status={status}")

    # B2 伪造 token → 401
    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/rules", "fake-token-abc")
    check("B2 伪造 token → 401", status == 401, f"status={status}")

    # B3 admin 写接口抽样 → 200
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules", admin_token, body={
        "ruleKey": "e2e_admin_write", "ruleName": "权限抽样",
        "groovyScript": "def evaluate(Map features) { return true }"})
    check("B3 admin 创建规则 → 200", status == 200, f"status={status}")

    # B4 造 viewer 测试用户（密码=admin123，复用 admin 的 BCrypt hash）
    admin_hash = psql("select password from sys_user where username='admin'")
    psql(f"delete from sys_user_role where user_id in (select id from sys_user where username='e2e_viewer')")
    psql(f"delete from sys_user where username='e2e_viewer'")
    psql(f"insert into sys_user (username, password, nickname, status) "
         f"values ('e2e_viewer', '{admin_hash}', 'E2E只读用户', 'ACTIVE')")
    psql(f"insert into sys_user_role (user_id, role_id) "
         f"select id, 4 from sys_user where username='e2e_viewer'")
    check("B4 造 viewer 用户（VIEWER 角色）", True)

    # B5 viewer 登录
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/auth/login",
                     body={"username": "e2e_viewer", "password": "admin123"})
    vtoken = r.get("token") if status == 200 else None
    check("B5 viewer 登录", status == 200 and vtoken, f"status={status}")

    # B6 viewer 只读接口 → 200
    for ep in ["/api/v1/rules", "/api/v1/metrics/overview", "/api/v1/analytics/overview",
               "/api/v1/decision-flows", "/api/v1/grayscale"]:
        status, _ = call("GET", f"{BASE_ADMIN}{ep}", vtoken)
        check(f"B6 viewer GET {ep} → 200", status == 200, f"status={status}")

    # B7 viewer 写接口 → 403
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules", vtoken, body={
        "ruleKey": "e2e_hack", "ruleName": "越权尝试",
        "groovyScript": "def evaluate(Map features) { return true }"})
    check("B7a viewer 创建规则 → 403", status == 403, f"status={status}")
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/features/catalog", vtoken, body={
        "code": "e2e_hack_feat", "name": "越权特征", "dataType": "NUMBER", "sourceType": "REQUEST"})
    check("B7b viewer 创建特征 → 403", status == 403, f"status={status}")
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows", vtoken, body={
        "flowKey": "e2e_hack_flow", "flowName": "越权流", "flowGraph": "{}"})
    check("B7c viewer 创建决策流 → 403", status == 403, f"status={status}")
    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/system/users", vtoken)
    check("B7d viewer 查用户列表 → 403", status == 403, f"status={status}")
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules/e2e_big_amount/enable", vtoken)
    check("B7e viewer 启用规则 → 403", status == 403, f"status={status}")

    # B8 admin 系统管理可读
    status, r = call("GET", f"{BASE_ADMIN}/api/v1/system/permissions", admin_token)
    check("B8 admin 权限清单可读", status == 200 and isinstance(r, list) and len(r) >= 30,
          f"status={status} count={len(r) if isinstance(r, list) else r}")

    # B9 清理 viewer
    psql("delete from sys_user_role where user_id in (select id from sys_user where username='e2e_viewer')")
    psql("delete from sys_user where username='e2e_viewer'")
    check("B9 清理 viewer 用户", True)


# ═══════════════ 场景 C：测试数据清理 ═══════════════
def scenario_cleanup(admin_token):
    print("\n▶ 场景 C：测试数据清理")
    for key in ["e2e_big_amount", "e2e_admin_write"]:
        status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/rules/{key}", admin_token)
        check(f"C1 删除规则 {key}", status in (200, 404), f"status={status}")
    status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/decision-flows/e2e_flow", admin_token)
    check("C2 删除决策流", status in (200, 404), f"status={status}")
    status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/features/catalog/txn_amount", admin_token)
    check("C3 删除特征（端点未提供时容忍 405）", status in (200, 404, 405), f"status={status}")


if __name__ == "__main__":
    print("═" * 56)
    print("E2E 全链路自动化测试（特征 → 规则 → 决策流 → 监控 + RBAC）")
    print("═" * 56)
    admin_token = scenario_full_chain()
    if not admin_token:
        print("登录失败，终止")
        sys.exit(2)
    scenario_rbac(admin_token)
    scenario_cleanup(admin_token)

    passed = sum(1 for _, ok, _ in results if ok)
    failed = len(results) - passed
    print("\n" + "═" * 56)
    print(f"结果：{passed}/{len(results)} 通过" + (f"，{failed} 失败" if failed else "，全部通过 ✅"))
    if failed:
        for name, ok, detail in results:
            if not ok:
                print(f"  ✗ {name}  {detail}")
    sys.exit(1 if failed else 0)
