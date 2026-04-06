#!/bin/bash

# Saga Rollback 演示腳本 - 模擬支付失敗場景
# 用途: momo 面試 live demo
#
# 架構: Orchestration-based Saga
# - SagaOrchestrator 集中管理流程
# - payment-service 90% 成功率模擬
# - 失敗自動觸發補償邏輯

set -e

# 顏色定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# API Base URL (根據你的實際部署調整)
BASE_URL=${BASE_URL:-"http://momo.local"}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Saga Rollback Demo - 支付失敗場景${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Step 0: 使用者登入
echo -e "${YELLOW}[Step 0]使用者登入...${NC}"

# 2. 拿 token
TOKEN=$(curl -s -X POST  "${BASE_URL}/realms/momo-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=momo-client" \
  -d "client_secret=momo-client-secret" \
  -d "username=testuser" \
  -d "password=password" \
  | jq -r '.access_token')
  
echo -e "${GREEN}Access Token:${NC}"
echo "$TOKEN"

# 3. 帶 token 打 API
USER_RESPONSE=$(curl -s "${BASE_URL}/api/users/usr-001" \
  -H "Authorization: Bearer $TOKEN" )
echo -e "\n${GREEN}Response:${NC}"

echo "$USER_RESPONSEE" | jq .

# Step 1: 創建訂單 (會扣減庫存)
echo -e "${YELLOW}[Step 1] 創建訂單 (扣減 Redis + MySQL 庫存)...${NC}"
ORDER_REQUEST='{
  "productId": "prod-001",
  "quantity": 2,
  "userId": "usr-001"
}'

echo -e "${GREEN}Request:${NC}"
echo "$ORDER_REQUEST" | jq .

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
echo -e "${YELLOW}[Step 2] 查詢訂單狀態...${NC}"
ORDER_STATUS=$(curl -s "${BASE_URL}/api/orders/${ORDER_ID}" \
-H "Authorization: Bearer $TOKEN")
echo "$ORDER_STATUS" | jq .
CURRENT_STATUS=$(echo "$ORDER_STATUS" | jq -r '.status')
echo -e "${GREEN}當前狀態: ${CURRENT_STATUS}${NC}\n"

sleep 2

# Step 3: 等待 Saga 自動執行
echo -e "\n${YELLOW}[Step 3] SagaOrchestrator 自動執行付款流程...${NC}"
echo -e "${GREEN}→ payment.process 事件已發送${NC}"
echo -e "${GREEN}→ payment-service 處理中 (90% 成功率模擬)${NC}"
echo -e "${GREEN}→ 等待 payment.result 回傳...${NC}"

sleep 5

# Step 4: 再次查詢訂單狀態 (Saga 完成後)
echo -e "\n${YELLOW}[Step 4] Saga 執行結果...${NC}"
FINAL_STATUS=$(curl -s "${BASE_URL}/api/orders/${ORDER_ID}" \
-H "Authorization: Bearer $TOKEN" )
echo "$FINAL_STATUS" | jq .

STATUS=$(echo "$FINAL_STATUS" | jq -r '.status')
if [ "$STATUS" = "CONFIRMED" ]; then
  echo -e "\n${GREEN}✓ 付款成功! 訂單完成${NC}"
  echo -e "${GREEN}✓ 訂單狀態: ${STATUS}${NC}"
  echo -e "${GREEN}✓ 後續會自動觸發 shipping-service 出貨${NC}"
elif [ "$STATUS" = "CANCELLED" ] || [ "$STATUS" = "FAILED" ]; then
  echo -e "\n${YELLOW}⚠ 付款失敗! Saga Rollback 已執行${NC}"
  echo -e "${GREEN}✓ 訂單狀態: ${STATUS}${NC}"
  echo -e "${GREEN}✓ 庫存已自動恢復 (MySQL + Redis)${NC}"
  echo -e "${GREEN}✓ 補償邏輯執行完成${NC}"
else
  echo -e "\n${RED}✗ 未預期的狀態: ${STATUS}${NC}"
fi

# Step 5: 說明可以查詢 saga_state 資料表
echo -e "\n${YELLOW}[Step 5] Saga 狀態追蹤${NC}"
echo -e "${GREEN}可以查詢 MySQL saga_state 資料表看完整流程:${NC}"
echo -e "${BLUE}SELECT * FROM saga_state WHERE order_id = '${ORDER_ID}';${NC}"

echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}   Demo 完成${NC}"
echo -e "${BLUE}========================================${NC}"

echo -e "\n${YELLOW}Saga 流程說明 (核心部分):${NC}"
echo "1. 用戶下單 → order-service 扣減庫存 (Redis + MySQL)"
echo "2. SagaOrchestrator 發送 payment.process"
echo "3. payment-service 處理付款 (90% 成功率模擬)"
echo "4a. 付款成功 → 訂單 CONFIRMED"
echo "4b. 付款失敗 → compensate() 補償:"
echo "    - 訂單狀態 → CANCELLED"
echo "    - MySQL 庫存回滾"
echo "    - Redis 庫存回滾"
echo "    - saga_state 記錄補償結果"

echo -e "\n${YELLOW}技術亮點:${NC}"
echo "• Orchestration-based Saga - SagaOrchestrator 集中管理"
echo "• saga_state 資料表 - 持久化狀態,斷電可恢復"
echo "• Kafka Event-Driven - 異步通訊解耦服務"
echo "• Compensating Transaction - 冪等性補償邏輯"
echo "• Redis + MySQL 雙層庫存 - 高並發 + 資料可靠性"

