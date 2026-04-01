#!/bin/bash

# 性能测试脚本
# 测试目标：验证规则引擎是否满足 50ms 性能要求 (P95)

echo "======================================"
echo "规则引擎性能测试"
echo "======================================"
echo ""

# 配置参数
API_URL="http://localhost:8080/api/v1/decide"
TOTAL_REQUESTS=1000
CONCURRENCY_LEVELS=(1 5 10 20 50)

# 测试数据
REQUEST_DATA='{
  "ruleId": "performance-test-rule",
  "script": "def risk = amount > 1000 ? \"HIGH\" : \"LOW\"; return risk;",
  "features": {
    "amount": 1500,
    "userId": "user123"
  },
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

# 创建临时文件存储请求体
TEMP_FILE=$(mktemp)
echo "$REQUEST_DATA" > "$TEMP_FILE"

# 检查工具是否可用
check_tool() {
  if ! command -v "$1" &> /dev/null; then
    echo "错误: $1 未安装。请安装: brew install $2"
    exit 1
  fi
}

echo "检查必要工具..."
check_tool "ab" "apache-bench"
check_tool "curl" "curl"
echo "✓ 工具检查完成"
echo ""

# 预热测试（让 JVM 预热）
echo "======================================"
echo "预热测试 (100 请求)"
echo "======================================"
ab -n 100 -c 10 -p "$TEMP_FILE" -T "application/json" "$API_URL" > /dev/null 2>&1
echo "✓ 预热完成"
echo ""
sleep 2

# 性能测试
echo "======================================"
echo "性能测试 (1000 请求)"
echo "======================================"

for concurrency in "${CONCURRENCY_LEVELS[@]}"; do
  echo ""
  echo "--------------------------------------"
  echo "并发级别: $concurrency"
  echo "--------------------------------------"

  # 运行 ab 测试
  RESULT=$(ab -n "$TOTAL_REQUESTS" -c "$concurrency" -p "$TEMP_FILE" -T "application/json" "$API_URL" 2>&1)

  # 提取关键指标
  RPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
  P95=$(echo "$RESULT" | grep "95%" | awk '{print $2}')
  P99=$(echo "$RESULT" | grep "99%" | awk '{print $2}')
  MEAN=$(echo "$RESULT" | grep "Time per request.*mean" | awk '{print $4}' | head -1)

  echo "请求/秒: $RPS"
  echo "平均响应时间: ${MEAN}ms"
  echo "P95 延迟: ${P95}ms"
  echo "P99 延迟: ${P99}ms"

  # 检查是否满足 50ms 目标
  P95_VALUE=$(echo "$P95" | tr -d '[:space:]')
  if (( $(echo "$P95_VALUE < 50" | bc -l) )); then
    echo "✓ 满足性能目标 (P95 < 50ms)"
  else
    echo "✗ 未满足性能目标 (P95 >= 50ms)"
  fi
done

echo ""
echo "======================================"
echo "详细测试报告"
echo "======================================"

# 运行完整测试并保存详细报告
REPORT_FILE="performance-report-$(date +%Y%m%d-%H%M%S).txt"
ab -n 1000 -c 20 -p "$TEMP_FILE" -T "application/json" "$API_URL" > "$REPORT_FILE" 2>&1

echo "详细报告已保存到: $REPORT_FILE"
echo ""

# 显示关键指标
echo "关键性能指标 (并发 20):"
grep -E "(Requests per second|Time per request|Percentage of the requests)" "$REPORT_FILE" | head -20

# 清理临时文件
rm -f "$TEMP_FILE"

echo ""
echo "======================================"
echo "测试完成"
echo "======================================"
