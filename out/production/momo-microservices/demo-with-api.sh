#!/bin/bash


set -e

# 顏色定義
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# API Base URL
BASE_URL=${BASE_URL:-"http://momo.local"}

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


echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Demo 完成${NC}"
echo -e "${BLUE}========================================${NC}\n"

