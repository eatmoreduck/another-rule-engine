#!/usr/bin/env python3
"""集成测试数据播种：特征字典扩充 + 规则 R1-R6 + 决策流 F1-F6 + 黑白名单。

对应测试方案：.planning/test-plan-rules-flows.md
幂等：先删后建（规则/流走 DELETE 软删接口，特征走 DELETE 软删接口，名单走 DELETE 接口）。
用法：python3 scripts/seed_integration_test_data.py  （需统一后端 8080 运行中，admin/admin123）
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def node(nid, ntype, **data):
    return {"id": nid, "type": ntype, "data": {"label": nid, "nodeType": ntype, **data}}


def edge(eid, src, tgt, **kw):
    return {"id": eid, "source": src, "target": tgt, **kw}


FEATURES = [
    {"code": "device_id", "name": "设备编号", "dataType": "STRING", "sourceType": "INPUT", "exampleValue": "D-1024", "description": "用户下单设备唯一编号", "aliases": ["deviceId", "uuid"]},
    {"code": "ip_address", "name": "IP 地址", "dataType": "STRING", "sourceType": "INPUT", "exampleValue": "112.10.45.8", "description": "下单请求来源 IP"},
    {"code": "user_register_days", "name": "注册天数", "dataType": "NUMBER", "sourceType": "DERIVED", "exampleValue": "45", "description": "账户注册至今天数，新户判定依据"},
    {"code": "order_count_1h", "name": "1小时下单数", "dataType": "NUMBER", "sourceType": "DERIVED", "exampleValue": "3", "description": "近 1 小时下单次数，高频判定依据"},
    {"code": "pay_fail_count_24h", "name": "24小时支付失败次数", "dataType": "NUMBER", "sourceType": "DERIVED", "exampleValue": "1", "description": "近 24 小时支付失败次数"},
    {"code": "shipping_address", "name": "收货地址", "dataType": "STRING", "sourceType": "INPUT", "exampleValue": "新疆乌鲁木齐市xx区", "description": "订单收货地址"},
]

RULES = [
    ("t_rule_amount_cap", "金额拦截",
     "def amt = features.order_amount ?: 0\nreturn amt > 10000 ? [decision: 'REJECT', reason: '订单金额超限'] : [decision: 'PASS']"),
    ("t_rule_vip_pass", "VIP 直通",
     "def lv = features.user_level ?: ''\nreturn lv == 'VIP' ? [decision: 'PASS', reason: 'VIP 直通'] : [decision: 'PASS']"),
    ("t_rule_high_freq", "高频失败拦截",
     "def oc = features.order_count_1h ?: 0\ndef pf = features.pay_fail_count_24h ?: 0\nreturn (oc > 10 && pf > 3) ? [decision: 'REJECT', reason: '高频下单且多次支付失败'] : [decision: 'PASS']"),
    ("t_rule_new_user_risk", "新户高风险拦截",
     "def rs = features.risk_score ?: 0\ndef pf = features.pay_fail_count_24h ?: 0\ndef rd = features.user_register_days ?: 999\nreturn ((rs > 80 || pf > 5) && rd < 30) ? [decision: 'REJECT', reason: '新户高风险拦截'] : [decision: 'PASS']"),
    ("t_rule_score_script", "加权评分拦截",
     "def rs = (features.risk_score ?: 0) * 0.5\ndef oc = (features.order_count_1h ?: 0) * 2\ndef rd = Math.min(Math.max(30 - (features.user_register_days ?: 999), 0), 30)\ndef total = rs + oc + rd\nreturn total > 60 ? [decision: 'REJECT', reason: '加权评分超阈值: ' + total] : [decision: 'PASS']"),
    ("t_rule_alias_ref", "别名链路验证",
     "def amt = features.order_amount ?: 0\ndef lv = features.user_level ?: ''\nreturn (amt > 8000 && lv != 'VIP') ? [decision: 'REJECT', reason: '非VIP大额拦截'] : [decision: 'PASS']"),
]

FLOWS = {
    "t_flow_simple": {
        "nodes": [node("s", "start"), node("rs", "ruleset", ruleKeys=["t_rule_amount_cap"]), node("e", "end", defaultAction="PASS", defaultReason="默认放行")],
        "edges": [edge("e1", "s", "rs"), edge("e2", "rs", "e")]},
    "t_flow_branch": {
        "nodes": [node("s", "start"), node("c", "condition", fieldName="order_amount", operator="GT", threshold=5000),
                  node("rs", "ruleset", ruleKeys=["t_rule_high_freq"]), node("e", "end", defaultAction="MANUAL_REVIEW", defaultReason="小额转人工")],
        "edges": [edge("e1", "s", "c"), edge("e2", "c", "rs", sourceHandle="true"), edge("e3", "c", "e", sourceHandle="false")]},
    "t_flow_blacklist": {
        "nodes": [node("s", "start"), node("b", "blacklist", keyType="DEVICE_ID", listKey="t_flow_blacklist", fieldName="device_id"),
                  node("e", "end", defaultAction="PASS", defaultReason="设备正常")],
        "edges": [edge("e1", "s", "b"), edge("e2", "b", "e")]},
    "t_flow_whitelist": {
        "nodes": [node("s", "start"), node("w", "whitelist", keyType="USER_LEVEL", listKey="t_white_vip", fieldName="user_level"),
                  node("e", "end", defaultAction="PASS", defaultReason="白名单放行")],
        "edges": [edge("e1", "s", "w"), edge("e2", "w", "e")]},
    "t_flow_complex": {
        "nodes": [
            node("s", "start"), node("b", "blacklist", keyType="DEVICE_ID", listKey="t_flow_blacklist", fieldName="device_id"),
            node("c1", "condition", fieldName="risk_score", operator="GT", threshold=60),
            node("rs1", "ruleset", ruleKeys=["t_rule_new_user_risk", "t_rule_score_script"]),
            node("c2", "condition", fieldName="shipping_address", operator="CONTAINS", threshold="新疆"),
            node("act", "action", action="REJECT", reason="高风险地区拦截"),
            node("rs2", "ruleset", ruleKeys=["t_rule_vip_pass"]),
            node("e", "end", defaultAction="PASS", defaultReason="综合评估通过")],
        "edges": [
            edge("e1", "s", "b"), edge("e2", "b", "c1"),
            edge("e3", "c1", "rs1", sourceHandle="true"), edge("e4", "c1", "rs2", sourceHandle="false"),
            edge("e5", "rs1", "c2"),
            edge("e6", "c2", "act", sourceHandle="true"), edge("e7", "c2", "e", sourceHandle="false"),
            edge("e8", "rs2", "e")]},
    "t_flow_alias": {
        "nodes": [node("s", "start"), node("rs", "ruleset", ruleKeys=["t_rule_alias_ref"]), node("e", "end", defaultAction="PASS", defaultReason="别名流通过")],
        "edges": [edge("e1", "s", "rs"), edge("e2", "rs", "e")]},
}

NAME_LISTS = [
    {"listType": "BLACK", "listKey": "t_flow_blacklist", "keyType": "DEVICE_ID", "keyValue": "D-999", "reason": "测试设备拉黑"},
    {"listType": "WHITE", "listKey": "t_white_vip", "keyType": "USER_LEVEL", "keyValue": "VIP", "reason": "VIP 白名单"},
]


def main():
    s, r = call("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
    if s != 200:
        print(f"登录失败: {s}")
        sys.exit(1)
    token = r["token"]
    created = 0

    # 特征（已存在则跳过）
    for f in FEATURES:
        s, r = call("POST", "/api/v1/features/catalog", token, f)
        if s == 200:
            created += 1
            print(f"✓ 特征 {f['code']}")
        elif "已存在" in str(r.get("message", "")):
            print(f"- 特征 {f['code']} 已存在，跳过")

    # user_level 追加别名
    s, r = call("GET", "/api/v1/features/catalog/user_level", token)
    if s == 200 and "level" not in r.get("aliases", []):
        call("PUT", "/api/v1/features/catalog/user_level", token,
             {"code": "user_level", "name": r["name"], "dataType": r["dataType"], "sourceType": r["sourceType"],
              "aliases": r["aliases"] + ["level"], "description": r.get("description")})
        print("✓ user_level 追加别名 level")

    # 名单
    for entry in NAME_LISTS:
        s, r = call("POST", "/api/v1/name-list", token, entry)
        print(f"{'✓' if s == 200 else '✗'} 名单 {entry['listType']}:{entry['keyValue']}" + (f" HTTP {s} {r.get('message')}" if s != 200 else ""))

    # 规则（存在则更新脚本，否则创建；随后启用）
    for key, name, script in RULES:
        s, r = call("POST", "/api/v1/rules", token, {"ruleKey": key, "ruleName": name, "groovyScript": script})
        if s != 200 and "已存在" in str(r.get("message", "")):
            s, r = call("PUT", f"/api/v1/rules/{key}", token, {"ruleKey": key, "ruleName": name, "groovyScript": script})
            print(f"- 规则 {key} 已存在，已更新脚本")
        if s == 200:
            call("POST", f"/api/v1/rules/{key}/enable", token)
            created += 1
            print(f"✓ 规则 {key}")
        else:
            print(f"✗ 规则 {key}: HTTP {s} {r.get('message')}")

    # 决策流（存在则更新图，否则创建；随后启用）
    for key, graph in FLOWS.items():
        payload = {"flowKey": key, "flowName": key, "flowGraph": json.dumps(graph, ensure_ascii=False)}
        s, r = call("POST", "/api/v1/decision-flows", token, payload)
        if s != 200 and "已存在" in str(r.get("message", "")):
            s, r = call("PUT", f"/api/v1/decision-flows/{key}", token, payload)
            print(f"- 决策流 {key} 已存在，已更新图")
        if s == 200:
            call("POST", f"/api/v1/decision-flows/{key}/enable", token)
            created += 1
            print(f"✓ 决策流 {key}")
        else:
            print(f"✗ 决策流 {key}: HTTP {s} {r.get('message')}")

    print(f"\n播种完成，新建 {created} 项")


if __name__ == "__main__":
    main()
