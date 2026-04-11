#!/bin/bash

# Saga Rollback 演示腳本 - 支援動態切換失敗模式
# 用途: momo 面試 live demo
#
# 架構: Orchestration-based Saga
# - SagaOrchestrator 集中管理流程
# - payment-service 可動態切換失敗模式
# - 失敗自動觸發補償邏輯

set -e

# 顏色定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# API Base URL (根據你的實際部署調整)
BASE_URL=${BASE_URL:-"http://momo.local"}

# Demo 模式:
# - "fail"    = 展示補償場景 (強制失敗)
# - "success" = 展示正常場景 (90% 成功)
# - "random"  = 隨機 (90% 成功率)
DEMO_MODE=${1:-"fail"}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Saga Demo - momo Staff Engineer${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Step 0: 使用者登入
echo -e "${YELLOW}[Step 0] 使用者登入...${NC}"

TOKEN=$(curl -s -X POST "${BASE_URL}/realms/momo-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=momo-client" \
  -d "client_secret=momo-client-secret" \
  -d "username=testuser" \
  -d "password=password" \
  | jq -r '.access_token')



if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo -e "${RED}✗ 登入失敗,請檢查 Keycloak 設定${NC}"
  exit 1
fi

echo -e "${GREEN}✓ 登入成功${NC}\n"
echo -e "${GREEN}Access Token:${NC}"
echo "$TOKEN"

sleep 1

# Step 0.5: 設定 Demo 模式
echo -e "${PURPLE}========================================${NC}"
echo -e "${PURPLE}   設定 Demo 模式: ${DEMO_MODE}${NC}"
echo -e "${PURPLE}========================================${NC}\n"

if [ "$DEMO_MODE" = "fail" ]; then
  echo -e "${YELLOW}[Demo] 開啟強制失敗模式 (展示 Saga 補償)...${NC}"
  FORCE_FAIL_RESPONSE=$(curl -s -X POST \
    "${BASE_URL}/api/payment/demo/force-fail/true" \
    -H "Authorization: Bearer $TOKEN")
  echo "$FORCE_FAIL_RESPONSE" | jq .
  echo -e "${RED}⚠️  下一筆訂單付款會失敗,觸發 Saga Rollback${NC}\n"
elif [ "$DEMO_MODE" = "success" ]; then
  echo -e "${YELLOW}[Demo] 關閉強制失敗 (展示正常流程)...${NC}"
  FORCE_FAIL_RESPONSE=$(curl -s -X POST \
    "${BASE_URL}/api/payment/demo/force-fail/false" \
    -H "Authorization: Bearer $TOKEN")
  echo "$FORCE_FAIL_RESPONSE" | jq .
  echo -e "${GREEN}✓ 恢復正常模式 (90% 成功率)${NC}\n"
else
  echo -e "${YELLOW}[Demo] 隨機模式 (不改變設定)${NC}"
  STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/payment/demo/status" \
    -H "Authorization: Bearer $TOKEN")
  echo "$STATUS_RESPONSE" | jq .
  echo ""
fi

sleep 2

# Step 1: 創建訂單 (會扣減庫存)
echo -e "${YELLOW}[Step 1] 創建訂單 (扣減 Redis + MySQL 庫存)...${NC}"
ORDER_REQUEST='{
  "productId": "prod-001",
  "quantity": 2,
  "userId": "usr-001"
}'

echo -e "${GREEN}Request:${NC}"
echo "$ORDER_REQUEST"

ORDER_RESPONSE=$(curl -s -X POST \
  "${BASE_URL}/api/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "$ORDER_REQUEST")

echo -e "\n${GREEN}Response:${NC}"
echo "$ORDER_RESPONSE" | jq .

ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.orderId')
echo -e "\n${GREEN}✓ 訂單已創建: ${ORDER_ID}${NC}"
echo -e "${GREEN}✓ 庫存已扣減${NC}\n"

sleep 2

# Step 2: 查詢訂單狀態 (應該是 PENDING 或 CREATED)
echo -e "${YELLOW}[Step 2] 查詢訂單初始狀態...${NC}"
ORDER_STATUS=$(curl -s "${BASE_URL}/api/orders/${ORDER_ID}" \
  -H "Authorization: Bearer $TOKEN")
echo "$ORDER_STATUS" | jq .
CURRENT_STATUS=$(echo "$ORDER_STATUS" | jq -r '.status')
echo -e "${GREEN}當前狀態: ${CURRENT_STATUS}${NC}\n"

sleep 2

# Step 3: 等待 Saga 自動執行
echo -e "${YELLOW}[Step 3] SagaOrchestrator 自動執行付款流程...${NC}"
echo -e "${GREEN}→ payment.process 事件已發送到 payment-service${NC}"
echo -e "${GREEN}→ payment-service 處理中${NC}"
if [ "$DEMO_MODE" = "fail" ]; then
  echo -e "${RED}→ (強制失敗模式：付款會失敗)${NC}"
elif [ "$DEMO_MODE" = "success" ]; then
  echo -e "${GREEN}→ (正常模式：90% 機率成功)${NC}"
else
  echo -e "${YELLOW}→ (隨機模式：90% 機率成功)${NC}"
fi
echo -e "${GREEN}→ 等待 payment.result 回傳...${NC}\n"

sleep 5

# Step 4: 查詢 Saga 執行結果
echo -e "${YELLOW}[Step 4] Saga 執行結果...${NC}"
FINAL_STATUS=$(curl -s "${BASE_URL}/api/orders/${ORDER_ID}" \
  -H "Authorization: Bearer $TOKEN")
echo "$FINAL_STATUS" | jq .

STATUS=$(echo "$FINAL_STATUS" | jq -r '.status')

echo ""
echo -e "${BLUE}========================================${NC}"
if [ "$STATUS" = "CONFIRMED" ]; then
  echo -e "${GREEN}✓ 付款成功! 訂單完成${NC}"
  echo -e "${GREEN}✓ 訂單狀態: ${STATUS}${NC}"
  echo -e "${GREEN}✓ 後續會自動觸發 shipping-service 出貨${NC}"
elif [ "$STATUS" = "CANCELLED" ] || [ "$STATUS" = "FAILED" ]; then
  echo -e "${YELLOW}⚠  付款失敗! Saga Rollback 已執行${NC}"
  echo -e "${GREEN}✓ 訂單狀態: ${STATUS}${NC}"
  echo -e "${GREEN}✓ 庫存已自動恢復 (MySQL + Redis)${NC}"
  echo -e "${GREEN}✓ 補償邏輯執行完成${NC}"
else
  echo -e "${RED}✗ 未預期的狀態: ${STATUS}${NC}"
fi
echo -e "${BLUE}========================================${NC}\n"



echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Demo 完成${NC}"
echo -e "${BLUE}========================================${NC}\n"

