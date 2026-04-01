#!/bin/bash

# Advanced Performance Test Script
echo "======================================"
echo "Rule Engine Advanced Performance Test"
echo "======================================"
echo ""

API_URL="http://localhost:8080/api/v1/decide"
TOTAL_REQUESTS=500

# Test scenarios
SCENARIO_1='{
  "ruleId": "simple-rule",
  "script": "amount > 1000 ? \"HIGH\" : \"LOW\"",
  "features": {"amount": 1500, "userId": "user123"},
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

SCENARIO_2='{
  "ruleId": "medium-rule",
  "script": "def riskScore = 0; riskScore += amount > 1000 ? 30 : 0; riskScore += userId.startsWith(\"vip\") ? 20 : 0; riskScore += amount > 5000 ? 40 : 0; def risk = riskScore > 50 ? \"HIGH\" : (riskScore > 20 ? \"MEDIUM\" : \"LOW\"); return risk;",
  "features": {"amount": 2500, "userId": "vip-user123"},
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

SCENARIO_3='{
  "ruleId": "complex-rule",
  "script": "import java.time.LocalDate; def today = LocalDate.now(); def hour = today.hour; def amountValue = amount; def userTier = userId.startsWith(\"vip\") ? \"VIP\" : \"NORMAL\"; def baseScore = 0; if (amountValue > 10000) baseScore += 50; else if (amountValue > 5000) baseScore += 30; else if (amountValue > 1000) baseScore += 10; if (userTier == \"VIP\") baseScore += 20; if (hour >= 2 && hour <= 6) baseScore += 30; else if (hour >= 23 || hour <= 1) baseScore += 15; def decision = baseScore >= 60 ? \"REJECT\" : (baseScore >= 30 ? \"REVIEW\" : \"PASS\"); return [decision: decision, score: baseScore, tier: userTier];",
  "features": {"amount": 8000, "userId": "vip-user123"},
  "requiredFeatures": [],
  "timeoutMs": 5000
}'

TEMP_FILE=$(mktemp)

# Test Scenario 1: Simple Rule
echo "--------------------------------------"
echo "Test Scenario: Simple Rule"
echo "--------------------------------------"
echo "$SCENARIO_1" > "$TEMP_FILE"
ab -n 50 -c 5 -p "$TEMP_FILE" -T "application/json" "$API_URL" > /dev/null 2>&1
sleep 1
RESULT=$(ab -n "$TOTAL_REQUESTS" -c 20 -p "$TEMP_FILE" -T "application/json" "$API_URL" 2>&1)
RPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
MEAN=$(echo "$RESULT" | grep "Time per request.*mean" | awk '{print $4}' | head -1)
P95=$(echo "$RESULT" | grep "95%" | awk '{print $2}')
P99=$(echo "$RESULT" | grep "99%" | awk '{print $2}')
echo "RPS: $RPS | Mean: ${MEAN}ms | P95: ${P95}ms | P99: ${P99}ms"
echo ""

# Test Scenario 2: Medium Rule
echo "--------------------------------------"
echo "Test Scenario: Medium Rule"
echo "--------------------------------------"
echo "$SCENARIO_2" > "$TEMP_FILE"
ab -n 50 -c 5 -p "$TEMP_FILE" -T "application/json" "$API_URL" > /dev/null 2>&1
sleep 1
RESULT=$(ab -n "$TOTAL_REQUESTS" -c 20 -p "$TEMP_FILE" -T "application/json" "$API_URL" 2>&1)
RPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
MEAN=$(echo "$RESULT" | grep "Time per request.*mean" | awk '{print $4}' | head -1)
P95=$(echo "$RESULT" | grep "95%" | awk '{print $2}')
P99=$(echo "$RESULT" | grep "99%" | awk '{print $2}')
echo "RPS: $RPS | Mean: ${MEAN}ms | P95: ${P95}ms | P99: ${P99}ms"
echo ""

# Test Scenario 3: Complex Rule
echo "--------------------------------------"
echo "Test Scenario: Complex Rule"
echo "--------------------------------------"
echo "$SCENARIO_3" > "$TEMP_FILE"
ab -n 50 -c 5 -p "$TEMP_FILE" -T "application/json" "$API_URL" > /dev/null 2>&1
sleep 1
RESULT=$(ab -n "$TOTAL_REQUESTS" -c 20 -p "$TEMP_FILE" -T "application/json" "$API_URL" 2>&1)
RPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
MEAN=$(echo "$RESULT" | grep "Time per request.*mean" | awk '{print $4}' | head -1)
P95=$(echo "$RESULT" | grep "95%" | awk '{print $2}')
P99=$(echo "$RESULT" | grep "99%" | awk '{print $2}')
echo "RPS: $RPS | Mean: ${MEAN}ms | P95: ${P95}ms | P99: ${P99}ms"
echo ""

rm -f "$TEMP_FILE"

echo "======================================"
echo "Advanced Performance Test Completed"
echo "======================================"
