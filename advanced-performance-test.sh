#!/bin/bash

# 高级性能测试脚本
# 测试不同复杂度规则下的性能表现

echo "======================================"
echo "规则引擎高级性能测试"
echo "======================================"
echo ""

API_URL="http://localhost:8080/api/v1/decide"
TOTAL_REQUESTS=500

# 测试场景定义
declare -A SCENARIOS
SCENARIOS["简单规则"]='{
  "ruleId": "simple-rule",
  "script": "amount > 1000 ? \"HIGH\" : \"LOW\"",
  "features": {"amount": 1500, "userId": "user123"},
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

SCENARIOS["中等规则"]='{
  "ruleId": "medium-rule",
  "script": "def riskScore = 0; riskScore += amount > 1000 ? 30 : 0; riskScore += userId.startsWith(\"vip\") ? 20 : 0; riskScore += amount > 5000 ? 40 : 0; def risk = riskScore > 50 ? \"HIGH\" : (riskScore > 20 ? \"MEDIUM\" : \"LOW\"); return risk;",
  "features": {"amount": 2500, "userId": "vip-user123"},
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

SCENARIOS["复杂规则"]='{
  "ruleId": "complex-rule",
  "script": "import java.time.LocalDate; def today = LocalDate.now(); def hour = today.hour; def amountValue = amount; def userTier = userId.startsWith(\"vip\") ? \"VIP\" : \"NORMAL\"; def baseScore = 0; if (amountValue > 10000) baseScore += 50; else if (amountValue > 5000) baseScore += 30; else if (amountValue > 1000) baseScore += 10; if (userTier == \"VIP\") baseScore += 20; if (hour >= 2 && hour <= 6) baseScore += 30; else if (hour >= 23 || hour <= 1) baseScore += 15; def decision = baseScore >= 60 ? \"REJECT\" : (baseScore >= 30 ? \"REVIEW\" : \"PASS\"); return [decision: decision, score: baseScore, tier: userTier];",
  "features": {"amount": 8000, "userId": "vip-user123"},
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

# 创建临时文件
TEMP_FILE=$(mktemp)

# 测试每个场景
for SCENARIO in "${!SCENARIOS[@]}"; do
  echo "--------------------------------------"
  echo "测试场景: $SCENARIO"
  echo "--------------------------------------"

  # 写入请求体
  echo "${SCENARIOS[$SCENARIO]}" > "$TEMP_FILE"

  # 预热
  ab -n 50 -c 5 -p "$TEMP_FILE" -T "application/json" "$API_URL" > /dev/null 2>&1
  sleep 1

  # 执行测试
  RESULT=$(ab -n "$TOTAL_REQUESTS" -c 20 -p "$TEMP_FILE" -T "application/json" "$API_URL" 2>&1)

  # 提取指标
  RPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
  MEAN=$(echo "$RESULT" | grep "Time per request.*mean" | awk '{print $4}' | head -1)
  P95=$(echo "$RESULT" | grep "95%" | awk '{print $2}')
  P99=$(echo "$RESULT" | grep "99%" | awk '{print $2}')
  FAILED=$(echo "$RESULT" | grep "Failed requests" | awk '{print $3}')

  echo "请求/秒: $RPS"
  echo "平均延迟: ${MEAN}ms"
  echo "P95 延迟: ${P95}ms"
  echo "P99 延迟: ${P99}ms"
  echo "失败请求: $FAILED"

  # 性能评估
  P95_VALUE=$(echo "$P95" | tr -d '[:space:]')
  if (( $(echo "$P95_VALUE < 50" | bc -l) )); then
    echo "✓ 满足 50ms 目标"
  else
    echo "✗ 未满足 50ms 目标"
  fi
  echo ""
done

# 清理
rm -f "$TEMP_FILE"

echo "======================================"
echo "高级性能测试完成"
echo "======================================"
