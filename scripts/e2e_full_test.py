#!/usr/bin/env python3
"""端到端全链路自动化测试：特征 → 规则 → 决策流 → 监控 + RBAC 权限矩阵。

用法：
    python3 scripts/e2e_full_test.py

前置：
    - admin-api(8080) / decision-api(8081) 运行中
    - PostgreSQL 可达（脚本会直插/清理 viewer 测试用户）
    - 初始管理员 admin/admin123

幂等性：测试资源以运行级时间戳后缀命名，重复执行不冲突。
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE_ADMIN = "http://localhost:8080"
BASE_DECISION = "http://localhost:8081"
# 语义化命名（可读优先）：运行前清场 + 运行后删除保证幂等，不用时间戳后缀
RULE_BIG = "e2e_big_amount_test"
RULE_ADMIN = "e2e_admin_rule_test"
FLOW = "e2e_flow_test"
FEATURE = "txn_amount_test"

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  ✓ " if ok else "  ✗ ") + name + (f"  [{detail}]" if detail and not ok else ""))


def call(method, url, token=None, body=None, timeout=15):
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
    cmd = ["/opt/homebrew/opt/postgresql@18/bin/psql", "-h", "192.168.5.200",
           "-U", "yare_app", "-d", "yare_engine", "-t", "-A", "-c", sql]
    env = {"PGPASSWORD": "ServBay.dev", "PATH": "/usr/bin:/bin:/opt/homebrew/bin"}
    return subprocess.run(cmd, capture_output=True, text=True, env=env).stdout.strip()


def scenario_full_chain():
    print("\n▶ 场景 A：特征 → 规则 → 决策流 → 监控 全链路")
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/auth/login",
                     body={"username": "admin", "password": "admin123"})
    token = r.get("token") if status == 200 else None
    check("A1 admin 登录", status == 200 and token, f"status={status}")
    H = token

    # 运行前清场：物理删除上轮残留（逻辑删除的行会占住 Key 且产生新旧双行，
    # 导致 findMain 语义错乱），保证幂等
    psql(f"delete from decision_flow_versions where flow_key='{FLOW}'")
    psql(f"delete from decision_flows where flow_key='{FLOW}'")
    psql(f"delete from rule_versions where rule_key in ('{RULE_BIG}','{RULE_ADMIN}')")
    psql(f"delete from rules where rule_key in ('{RULE_BIG}','{RULE_ADMIN}')")
    call("DELETE", f"{BASE_ADMIN}/api/v1/rules/{RULE_BIG}", H)
    call("DELETE", f"{BASE_ADMIN}/api/v1/rules/{RULE_ADMIN}", H)
    call("DELETE", f"{BASE_ADMIN}/api/v1/decision-flows/{FLOW}", H)

    status, r = call("POST", f"{BASE_ADMIN}/api/v1/features/catalog", H, body={
        "code": FEATURE, "name": "交易金额", "dataType": "NUMBER",
        "sourceType": "INPUT", "exampleValue": "12000", "description": "E2E 测试特征",
    })
    check("A2 创建特征", status == 200 or (status == 400 and "已存在" in json.dumps(r, ensure_ascii=False)),
          f"status={status} body={r}")

    script = ("def evaluate(Map features) {\n"
              "  if (features.txn_amount > 10000) {\n"
              "    return [decision: 'REJECT', reason: '大额拒绝']\n"
              "  }\n"
              "  return [decision: 'PASS', reason: '金额正常']\n"
              "}")
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/rules", H, body={
        "ruleKey": RULE_BIG, "ruleName": "E2E大额规则", "groovyScript": script})
    check("A3 创建规则", status == 200, f"status={status} body={r}")

    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules/{RULE_BIG}/enable", H)
    check("A4 启用规则", status == 200, f"status={status}")

    status, r = call("POST", f"{BASE_ADMIN}/api/v1/test/rules/{RULE_BIG}/execute", H,
                     body={"txn_amount": 20000})
    check("A5a 测试 20000 → REJECT", status == 200 and r.get("decision") == "REJECT",
          f"status={status} got={r.get('decision')}")
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/test/rules/{RULE_BIG}/execute", H,
                     body={"txn_amount": 500})
    check("A5b 测试 500 → PASS", status == 200 and r.get("decision") == "PASS",
          f"status={status} got={r.get('decision')}")

    status, r = call("POST", f"{BASE_DECISION}/api/v1/decide", H, body={
        "ruleId": RULE_BIG, "script": "", "features": {"txn_amount": 20000}})
    check("A6 按规则键决策 20000", status == 200, f"status={status}")

    graph = {
        "nodes": [
            {"id": "n_start", "type": "start", "position": {"x": 80, "y": 60},
             "data": {"label": "开始", "nodeType": "start"}},
            {"id": "n_ruleset", "type": "ruleset", "position": {"x": 320, "y": 60},
             "data": {"label": "规则集", "nodeType": "ruleset", "ruleKeys": [RULE_BIG]}},
            {"id": "n_end", "type": "end", "position": {"x": 560, "y": 60},
             "data": {"label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "流程通过"}},
        ],
        "edges": [
            {"id": "e1", "source": "n_start", "target": "n_ruleset"},
            {"id": "e2", "source": "n_ruleset", "target": "n_end"},
        ],
    }
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows", H, body={
        "flowKey": FLOW, "flowName": "E2E决策流", "flowDescription": "全链路测试流",
        "flowGraph": json.dumps(graph)})
    check("A7 创建决策流", status == 200, f"status={status} body={r}")

    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows/{FLOW}/enable", H)
    check("A8 启用决策流", status == 200, f"status={status}")

    # 流执行端点的请求体为裸特征 Map（无包装字段）
    status, r = call("POST", f"{BASE_DECISION}/api/v1/decision-flows/{FLOW}/execute", H,
                     body={"txn_amount": 20000})
    check("A9a 执行流 20000 → REJECT", status == 200 and r.get("decision") == "REJECT",
          f"status={status} got={r.get('decision')} body={json.dumps(r, ensure_ascii=False)[:160]}")
    status, r = call("POST", f"{BASE_DECISION}/api/v1/decision-flows/{FLOW}/execute", H,
                     body={"txn_amount": 500})
    check("A9b 执行流 500 → PASS", status == 200 and r.get("decision") == "PASS",
          f"status={status} got={r.get('decision')}")

    status, r = call("GET", f"{BASE_ADMIN}/api/v1/metrics/overview", H)
    total = r.get("totalExecutions", 0) if isinstance(r, dict) else 0
    check("A10 监控 overview 含执行数据", status == 200 and total > 0,
          f"status={status} totalExecutions={total}")
    status, r = call("GET", f"{BASE_ADMIN}/api/v1/logs/recent?limit=5", H)
    check("A11 最近执行日志非空", status == 200 and isinstance(r, list) and len(r) > 0,
          f"status={status} len={len(r) if isinstance(r, list) else r}")

    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/name-list", H)
    check("A12 名单列表可读", status == 200, f"status={status}")
    return token


def scenario_rbac(admin_token):
    print("\n▶ 场景 B：RBAC 权限矩阵")
    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/rules")
    check("B1 无 token → 401", status == 401, f"status={status}")

    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/rules", "fake-token-abc")
    check("B2 伪造 token → 401", status == 401, f"status={status}")

    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules", admin_token, body={
        "ruleKey": RULE_ADMIN, "ruleName": "权限抽样",
        "groovyScript": "def evaluate(Map features) { return true }"})
    check("B3 admin 创建规则 → 200", status == 200, f"status={status}")

    admin_hash = psql("select password from sys_user where username='admin'")
    psql("delete from sys_user_role where user_id in (select id from sys_user where username='e2e_viewer')")
    psql("delete from sys_user where username='e2e_viewer'")
    psql(f"insert into sys_user (username, password, nickname, status) "
         f"values ('e2e_viewer', '{admin_hash}', 'E2E只读用户', 'ACTIVE')")
    psql("insert into sys_user_role (user_id, role_id) "
         "select id, 4 from sys_user where username='e2e_viewer'")
    check("B4 造 viewer 用户（VIEWER 角色）", True)

    status, r = call("POST", f"{BASE_ADMIN}/api/v1/auth/login",
                     body={"username": "e2e_viewer", "password": "admin123"})
    vtoken = r.get("token") if status == 200 else None
    check("B5 viewer 登录", status == 200 and vtoken, f"status={status}")

    for ep in ["/api/v1/rules", "/api/v1/metrics/overview", "/api/v1/analytics/overview",
               "/api/v1/decision-flows", "/api/v1/grayscale"]:
        status, _ = call("GET", f"{BASE_ADMIN}{ep}", vtoken)
        check(f"B6 viewer GET {ep} → 200", status == 200, f"status={status}")

    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules", vtoken, body={
        "ruleKey": "e2e_hack_rule_test", "ruleName": "越权尝试",
        "groovyScript": "def evaluate(Map features) { return true }"})
    check("B7a viewer 创建规则 → 403", status == 403, f"status={status}")
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/features/catalog", vtoken, body={
        "code": "e2e_hack_feat_test", "name": "越权特征", "dataType": "NUMBER", "sourceType": "INPUT"})
    check("B7b viewer 创建特征 → 403", status == 403, f"status={status}")
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows", vtoken, body={
        "flowKey": "e2e_hack_flow_test", "flowName": "越权流", "flowGraph": "{}"})
    check("B7c viewer 创建决策流 → 403", status == 403, f"status={status}")
    status, _ = call("GET", f"{BASE_ADMIN}/api/v1/system/users", vtoken)
    check("B7d viewer 查用户列表 → 403", status == 403, f"status={status}")
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/rules/{RULE_BIG}/enable", vtoken)
    check("B7e viewer 启用规则 → 403", status == 403, f"status={status}")

    status, r = call("GET", f"{BASE_ADMIN}/api/v1/system/permissions", admin_token)
    check("B8 admin 权限清单可读", status == 200 and isinstance(r, list) and len(r) >= 30,
          f"status={status} count={len(r) if isinstance(r, list) else r}")

    psql("delete from sys_user_role where user_id in (select id from sys_user where username='e2e_viewer')")
    psql("delete from sys_user where username='e2e_viewer'")
    check("B9 清理 viewer 用户", True)


def scenario_cleanup(admin_token):
    print("\n▶ 场景 C：测试数据清理")
    for key in [RULE_BIG, RULE_ADMIN]:
        status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/rules/{key}", admin_token)
        check(f"C1 删除规则 {key}", status in (200, 400, 404), f"status={status}")
    status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/decision-flows/{FLOW}", admin_token)
    check("C2 删除决策流", status in (200, 400, 404), f"status={status}")


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
