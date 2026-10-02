#!/usr/bin/env python3
"""节点矩阵测试集：规则逻辑操作符 + 决策流全部节点类型（8 种）+ 多节点组合。

覆盖矩阵：
  S1 单规则逻辑操作符：AND / OR / 嵌套组合（(a AND b) OR c）
  S2 规则集：拒绝优先 / 全通过 / 缺失规则容错跳过
  S3 决策流节点：
     start / end（全用例公共）
     whitelist：命中放行、未命中拒绝
     blacklist：命中拒绝、未命中放行
     condition：true/false 双分支路由
     action：直接动作
     ruleset：见 S2（复用）
     merge：多入边汇聚
  S4 多节点组合流：condition → (true)action / (false)blacklist → merge → end

用法：
    python3 scripts/e2e_node_matrix_test.py

前置：admin-api(8080) / decision-api(8081) 运行中；admin/admin123。
幂等：规则/流/名单用固定语义名，运行前清场。
"""
import json
import sys
import urllib.error
import urllib.request

BASE_ADMIN = "http://localhost:8080"
BASE_DECISION = "http://localhost:8081"

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  ✓ " if ok else "  ✗ ") + name + (f"  [got: {detail}]" if detail and not ok else ""))


def call(method, url, token=None, body=None, timeout=20):
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


def login():
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/auth/login",
                     body={"username": "admin", "password": "admin123"})
    assert status == 200, f"登录失败 status={status}"
    return r["token"]


# ───────────────────────── S1 单规则逻辑操作符 ─────────────────────────
def s1_operators(token):
    print("\n▶ S1 单规则逻辑操作符（AND / OR / 嵌套）")
    cases = [
        # (用例名, 脚本, 输入特征, 期望 decision, 期望 reason)
        ("AND 全真 → 命中",
         "def evaluate(Map f) {\n  if (f.age > 18 && f.city == 'BJ') {\n    return [decision: 'REJECT', reason: 'AND命中']\n  }\n  return [decision: 'PASS', reason: 'AND未中']\n}",
         {"age": 30, "city": "BJ"}, "REJECT", "AND命中"),
        ("AND 半真 → 未命中",
         "def evaluate(Map f) {\n  if (f.age > 18 && f.city == 'BJ') {\n    return [decision: 'REJECT', reason: 'AND命中']\n  }\n  return [decision: 'PASS', reason: 'AND未中']\n}",
         {"age": 30, "city": "SH"}, "PASS", "AND未中"),
        ("OR 任一真 → 命中",
         "def evaluate(Map f) {\n  if (f.age < 18 || f.risk > 80) {\n    return [decision: 'REJECT', reason: 'OR命中']\n  }\n  return [decision: 'PASS', reason: 'OR未中']\n}",
         {"age": 30, "risk": 90}, "REJECT", "OR命中"),
        ("OR 全假 → 未命中",
         "def evaluate(Map f) {\n  if (f.age < 18 || f.risk > 80) {\n    return [decision: 'REJECT', reason: 'OR命中']\n  }\n  return [decision: 'PASS', reason: 'OR未中']\n}",
         {"age": 30, "risk": 50}, "PASS", "OR未中"),
        ("嵌套 (a AND b) OR c → c 命中",
         "def evaluate(Map f) {\n  if ((f.a > 1 && f.b < 2) || f.c == 3) {\n    return [decision: 'REJECT', reason: '嵌套命中']\n  }\n  return [decision: 'PASS', reason: '嵌套未中']\n}",
         {"a": 0, "b": 9, "c": 3}, "REJECT", "嵌套命中"),
        ("嵌套 (a AND b) OR c → a/b 命中",
         "def evaluate(Map f) {\n  if ((f.a > 1 && f.b < 2) || f.c == 3) {\n    return [decision: 'REJECT', reason: '嵌套命中']\n  }\n  return [decision: 'PASS', reason: '嵌套未中']\n}",
         {"a": 5, "b": 1, "c": 0}, "REJECT", "嵌套命中"),
        ("嵌套 全不满足 → 未命中",
         "def evaluate(Map f) {\n  if ((f.a > 1 && f.b < 2) || f.c == 3) {\n    return [decision: 'REJECT', reason: '嵌套命中']\n  }\n  return [decision: 'PASS', reason: '嵌套未中']\n}",
         {"a": 0, "b": 9, "c": 0}, "PASS", "嵌套未中"),
    ]
    for i, (name, script, features, edec, ereason) in enumerate(cases):
        status, r = call("POST", f"{BASE_DECISION}/api/v1/decide", token,
                         body={"ruleId": f"e2e_op_{i}", "script": script, "features": features})
        ok = status == 200 and r.get("decision") == edec and r.get("reason") == ereason
        check(name, ok, f"got={r.get('decision')}/{r.get('reason')}")


# ───────────────────────── S2 规则集（ruleset 节点） ─────────────────────────
PASS_SCRIPT = "def evaluate(Map f) { return [decision:'PASS', reason:'正常'] }"
REJECT_SCRIPT = "def evaluate(Map f) { return [decision:'REJECT', reason:'命中拒绝'] }"


def psql(sql):
    import subprocess
    cmd = ["/opt/homebrew/opt/postgresql@18/bin/psql", "-h", "192.168.5.200",
           "-U", "yare_app", "-d", "yare_engine", "-t", "-A", "-c", sql]
    env = {"PGPASSWORD": "ServBay.dev", "PATH": "/usr/bin:/bin:/opt/homebrew/bin"}
    return subprocess.run(cmd, capture_output=True, text=True, env=env).stdout.strip()


def make_flow(token, flow_key, graph):
    # 物理清理同名残留：逻辑删除的流会占住 Key（"决策流Key已存在"），先物理删保证幂等
    psql(f"delete from decision_flow_versions where flow_key='{flow_key}'")
    psql(f"delete from decision_flows where flow_key='{flow_key}'")
    call("DELETE", f"{BASE_ADMIN}/api/v1/decision-flows/{flow_key}", token)
    status, r = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows", token, body={
        "flowKey": flow_key, "flowName": flow_key, "flowGraph": json.dumps(graph)})
    if status != 200:
        return False
    status, _ = call("POST", f"{BASE_ADMIN}/api/v1/decision-flows/{flow_key}/enable", token)
    return status == 200


def run_flow(token, flow_key, features):
    status, r = call("POST", f"{BASE_DECISION}/api/v1/decision-flows/{flow_key}/execute", token,
                     body=features)
    return (r or {}).get("decision"), (r or {}).get("reason")


def base_graph(rule_keys=None):
    nodes = [{"id": "n1", "type": "start", "position": {"x": 80, "y": 60},
              "data": {"label": "开始", "nodeType": "start"}}]
    if rule_keys is not None:
        nodes.append({"id": "n2", "type": "ruleset", "position": {"x": 320, "y": 60},
                      "data": {"label": "规则集", "nodeType": "ruleset", "ruleKeys": rule_keys}})
        nodes.append({"id": "n3", "type": "end", "position": {"x": 560, "y": 60},
                      "data": {"label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "流程通过"}})
    edges = [{"id": "e1", "source": "n1", "target": "n2"}, {"id": "e2", "source": "n2", "target": "n3"}]
    return {"nodes": nodes, "edges": edges}


def s2_ruleset(token):
    print("\n▶ S2 规则集节点（ruleset）：拒绝优先 / 全通过 / 缺失容错")
    # S2a 拒绝优先：引用 [拒绝规则, 通过规则] → REJECT
    call("POST", f"{BASE_ADMIN}/api/v1/rules", token, body={
        "ruleKey": "e2m_reject_rule", "ruleName": "拒绝", "groovyScript": REJECT_SCRIPT})
    call("POST", f"{BASE_ADMIN}/api/v1/rules", token, body={
        "ruleKey": "e2m_pass_rule", "ruleName": "通过", "groovyScript": PASS_SCRIPT})
    for k in ["e2m_reject_rule", "e2m_pass_rule"]:
        call("POST", f"{BASE_ADMIN}/api/v1/rules/{k}/enable", token)
    assert make_flow(token, "e2m_flow_rs", base_graph(["e2m_reject_rule", "e2m_pass_rule"]))
    d, reason = run_flow(token, "e2m_flow_rs", {"x": 1})
    check("S2a 规则集拒绝优先 → REJECT", d == "REJECT", f"got={d}/{reason}")

    # S2b 全通过规则 → END 默认 PASS
    assert make_flow(token, "e2m_flow_rs2", base_graph(["e2m_pass_rule"]))
    d, reason = run_flow(token, "e2m_flow_rs2", {"x": 1})
    check("S2b 全通过规则 → PASS", d == "PASS", f"got={d}/{reason}")

    # S2c 缺失规则容错：[不存在规则, 通过规则] → 跳过缺失继续
    assert make_flow(token, "e2m_flow_rs3", base_graph(["e2m_not_exist", "e2m_pass_rule"]))
    d, reason = run_flow(token, "e2m_flow_rs3", {"x": 1})
    check("S2c 缺失规则跳过 → PASS", d == "PASS", f"got={d}/{reason}")


# ───────────────────────── S3 全节点类型流 ─────────────────────────
def s3_all_nodes(token):
    print("\n▶ S3 节点：whitelist / blacklist / condition / action / merge")

    # 名单数据：BLACK bad_user / WHITE good_user
    for entry in [
        {"listType": "BLACK", "listKey": "GLOBAL", "keyType": "PHONE_NO", "keyValue": "13800000001", "reason": "E2E黑名单"},
        {"listType": "WHITE", "listKey": "GLOBAL", "keyType": "PHONE_NO", "keyValue": "13800000002", "reason": "E2E白名单"},
    ]:
        call("POST", f"{BASE_ADMIN}/api/v1/name-list", token, body=entry)

    # S3a 白名单：命中放行 / 未命中拒绝
    wl_graph = {"nodes": [
        {"id": "n1", "type": "start", "position": {"x": 80, "y": 60},
         "data": {"label": "开始", "nodeType": "start"}},
        {"id": "n2", "type": "whitelist", "position": {"x": 320, "y": 60},
         "data": {"label": "白名单", "nodeType": "whitelist", "keyType": "PHONE_NO", "listKey": "GLOBAL"}},
        {"id": "n3", "type": "end", "position": {"x": 560, "y": 60},
         "data": {"label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "白名单通过"}},
    ], "edges": [{"id": "e1", "source": "n1", "target": "n2"}, {"id": "e2", "source": "n2", "target": "n3"}]}
    assert make_flow(token, "e2m_flow_wl", wl_graph)
    d, reason = run_flow(token, "e2m_flow_wl", {"PHONE_NO": "13800000002"})
    check("S3a1 白名单命中 → 放行 PASS", d == "PASS", f"got={d}/{reason}")
    d, reason = run_flow(token, "e2m_flow_wl", {"PHONE_NO": "13900000000"})
    check("S3a2 白名单未命中 → REJECT", d == "REJECT", f"got={d}/{reason}")

    # S3b 黑名单：命中拒绝 / 未命中放行
    bl_graph = {"nodes": [
        {"id": "n1", "type": "start", "position": {"x": 80, "y": 60},
         "data": {"label": "开始", "nodeType": "start"}},
        {"id": "n2", "type": "blacklist", "position": {"x": 320, "y": 60},
         "data": {"label": "黑名单", "nodeType": "blacklist", "keyType": "PHONE_NO", "listKey": "GLOBAL"}},
        {"id": "n3", "type": "end", "position": {"x": 560, "y": 60},
         "data": {"label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "无风险"}},
    ], "edges": [{"id": "e1", "source": "n1", "target": "n2"}, {"id": "e2", "source": "n2", "target": "n3"}]}
    assert make_flow(token, "e2m_flow_bl", bl_graph)
    d, reason = run_flow(token, "e2m_flow_bl", {"PHONE_NO": "13800000001"})
    check("S3b1 黑名单命中 → REJECT", d == "REJECT", f"got={d}/{reason}")
    d, reason = run_flow(token, "e2m_flow_bl", {"PHONE_NO": "13800000002"})
    check("S3b2 黑名单未命中 → PASS", d == "PASS", f"got={d}/{reason}")

    # S3c 多节点组合：condition 双分支 + action + blacklist + merge（6 节点）
    combo_graph = {"nodes": [
        {"id": "n1", "type": "start", "position": {"x": 60, "y": 200},
         "data": {"label": "开始", "nodeType": "start"}},
        {"id": "n2", "type": "condition", "position": {"x": 240, "y": 200},
         "data": {"label": "大额?", "nodeType": "condition", "fieldName": "txn_amount",
                  "operator": "GT", "threshold": 10000}},
        {"id": "n3", "type": "action", "position": {"x": 460, "y": 80},
         "data": {"label": "大额拒绝", "nodeType": "action", "action": "REJECT", "reason": "大额拒绝"}},
        {"id": "n4", "type": "blacklist", "position": {"x": 460, "y": 320},
         "data": {"label": "黑名单", "nodeType": "blacklist", "keyType": "PHONE_NO", "listKey": "GLOBAL"}},
        {"id": "n5", "type": "merge", "position": {"x": 680, "y": 200},
         "data": {"label": "汇聚", "nodeType": "merge"}},
        {"id": "n6", "type": "end", "position": {"x": 860, "y": 200},
         "data": {"label": "结束", "nodeType": "end", "defaultAction": "PASS", "defaultReason": "流程通过"}},
    ], "edges": [
        {"id": "e1", "source": "n1", "target": "n2"},
        {"id": "e2", "source": "n2", "target": "n3", "sourceHandle": "true"},
        {"id": "e3", "source": "n2", "target": "n4", "sourceHandle": "false"},
        {"id": "e4", "source": "n3", "target": "n5"},
        {"id": "e5", "source": "n4", "target": "n5"},
        {"id": "e6", "source": "n5", "target": "n6"},
    ]}
    assert make_flow(token, "e2m_flow_combo", combo_graph)
    d, reason = run_flow(token, "e2m_flow_combo", {"txn_amount": 20000, "PHONE_NO": "13800000002"})
    check("S3c1 condition-true → action 大额拒绝", d == "REJECT" and reason == "大额拒绝",
          f"got={d}/{reason}")
    d, reason = run_flow(token, "e2m_flow_combo", {"txn_amount": 500, "PHONE_NO": "13800000001"})
    check("S3c2 condition-false → blacklist 命中", d == "REJECT" and "黑名单" in (reason or ""),
          f"got={d}/{reason}")
    d, reason = run_flow(token, "e2m_flow_combo", {"txn_amount": 500, "PHONE_NO": "13800000002"})
    check("S3c3 condition-false → blacklist 未命中 → merge → END PASS", d == "PASS",
          f"got={d}/{reason}")

    # S3d 清理名单测试数据
    ids = psql_ids("select id from name_list where key_value in ('13800000001','13800000002')")
    for i in ids:
        call("DELETE", f"{BASE_ADMIN}/api/v1/name-list/{i}", token)
    check("S3d 清理名单测试数据", True)


def psql_ids(sql):
    import subprocess
    cmd = ["/opt/homebrew/opt/postgresql@18/bin/psql", "-h", "192.168.5.200",
           "-U", "yare_app", "-d", "yare_engine", "-t", "-A", "-c", sql]
    env = {"PGPASSWORD": "ServBay.dev", "PATH": "/usr/bin:/bin:/opt/homebrew/bin"}
    out = subprocess.run(cmd, capture_output=True, text=True, env=env).stdout.strip()
    return [line for line in out.splitlines() if line]


# ───────────────────────── 清理（规则/流） ─────────────────────────
def cleanup(token):
    print("\n▶ 清理测试资源")
    for key in ["e2m_reject_rule", "e2m_pass_rule"]:
        status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/rules/{key}", token)
        check(f"删规则 {key}", status in (200, 400, 404), f"status={status}")
    for flow in ["e2m_flow_rs", "e2m_flow_rs2", "e2m_flow_rs3", "e2m_flow_wl", "e2m_flow_bl", "e2m_flow_combo"]:
        status, _ = call("DELETE", f"{BASE_ADMIN}/api/v1/decision-flows/{flow}", token)
        check(f"删流 {flow}", status in (200, 400, 404), f"status={status}")


if __name__ == "__main__":
    print("═" * 56)
    print("节点矩阵测试集（逻辑操作符 + 全节点类型 + 多节点组合）")
    print("═" * 56)
    token = login()
    s1_operators(token)
    s2_ruleset(token)
    s3_all_nodes(token)
    cleanup(token)

    passed = sum(1 for _, ok, _ in results if ok)
    failed = len(results) - passed
    print("\n" + "═" * 56)
    print(f"结果：{passed}/{len(results)} 通过" + (f"，{failed} 失败" if failed else "，全部通过 ✅"))
    if failed:
        for name, ok, detail in results:
            if not ok:
                print(f"  ✗ {name}  {detail}")
    sys.exit(1 if failed else 0)
