#!/usr/bin/env bash
# =====================================================
# 公地引擎冒烟测试脚本
#
# 验证平台启动后五大模块端到端可用。
# 前置条件：
#   1. docker compose up -d（PostgreSQL + Redis 运行中）
#   2. ./gradlew bootRun（应用启动在 localhost:8080）
#
# 使用：
#   bash deployments/smoke-test.sh
# =====================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }

check() {
    local name="$1"
    local condition="$2"
    if [ "$condition" = "true" ]; then
        green "  ✅ $name"
        PASS=$((PASS + 1))
    else
        red "  ❌ $name"
        FAIL=$((FAIL + 1))
    fi
}

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  公地引擎冒烟测试"
echo "  目标: $BASE_URL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ━━━ 1. 健康检查 ━━━
echo ""
echo "▶ 平台健康检查"
HEALTH=$(curl -sf "$BASE_URL/actuator/health" 2>/dev/null || echo "")
check "平台启动" "$([ -n "$HEALTH" ] && echo true || echo false)"

if [ -z "$HEALTH" ]; then
    red "平台未运行！请先 docker compose up + ./gradlew bootRun"
    exit 1
fi

# ━━━ 2. 注册消费者 ━━━
echo ""
echo "▶ 注册消费者"
CONSUMER=$(curl -sf -X POST "$BASE_URL/api/v1/members/register" \
    -H "Content-Type: application/json" \
    -d '{"name":"测试消费者","phone":"13900000001","roles":["CONSUMER"]}' 2>/dev/null || echo "")
CONSUMER_ID=$(echo "$CONSUMER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
check "消费者注册" "$([ -n "$CONSUMER_ID" ] && echo true || echo false)"

# ━━━ 3. 注册劳动者 ━━━
echo ""
echo "▶ 注册劳动者"
WORKER=$(curl -sf -X POST "$BASE_URL/api/v1/members/register" \
    -H "Content-Type: application/json" \
    -d '{"name":"测试骑手","phone":"13900000002","roles":["WORKER"]}' 2>/dev/null || echo "")
WORKER_ID=$(echo "$WORKER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
check "劳动者注册" "$([ -n "$WORKER_ID" ] && echo true || echo false)"

# ━━━ 4. 劳动者上报位置 ━━━
echo ""
echo "▶ 劳动者上报位置"
LOC_RES=$(curl -sf -X POST "$BASE_URL/api/v1/matching/workers/$WORKER_ID/location" \
    -H "Content-Type: application/json" \
    -d '{"name":"测试骑手","lat":39.9850,"lng":116.3080,"serviceTypes":["RIDE_HAILING"],"rating":4.9}' 2>/dev/null || echo "")
check "位置上报" "$(echo "$LOC_RES" | grep -q "ok" && echo true || echo false)"

# ━━━ 5. 匹配引擎 ━━━
echo ""
echo "▶ 自动匹配"
MATCH=$(curl -sf -X POST "$BASE_URL/api/v1/matching/match/auto" \
    -H "Content-Type: application/json" \
    -d '{"consumerId":"'"$CONSUMER_ID"'","serviceType":"RIDE_HAILING","pickupLat":39.9847,"pickupLng":116.3076,"radiusMeters":5000}' 2>/dev/null || echo "")
MATCHED=$(echo "$MATCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('matched',False))" 2>/dev/null || echo "False")
check "匹配成功" "$([ "$MATCHED" = "True" ] && echo true || echo false)"

# ━━━ 6. 匹配策略查询 ━━━
echo ""
echo "▶ 匹配引擎状态"
STRAT=$(curl -sf "$BASE_URL/api/v1/matching/health" 2>/dev/null || echo "")
check "策略列表可用" "$(echo "$STRAT" | grep -q "availableStrategies" && echo true || echo false)"

# ━━━ 7. 统计 ━━━
echo ""
echo "▶ 会员统计"
STATS=$(curl -sf "$BASE_URL/api/v1/members/stats" 2>/dev/null || echo "")
check "统计数据返回" "$([ -n "$STATS" ] && echo true || echo false)"

# ━━━ 结果汇总 ━━━
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
green "  通过: $PASS"
if [ "$FAIL" -gt 0 ]; then
    red "  失败: $FAIL"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit 1
else
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    green "  全部通过！公地引擎运行正常 ✅"
    exit 0
fi
