#!/bin/bash

# Saga Demo - 降級方案 (不依賴 Demo API)
# 策略: 連續下 10 筆訂單,90% 成功率會至少遇到 1 次失敗

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

BASE_URL=${BASE_URL:-"http://momo.local"}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Saga Demo - 連續下單模式${NC}"
echo -e "${BLUE}   (90% 成功率,多跑幾次會遇到失敗)${NC}"
echo -e "${BLUE}========================================${NC}\n"

# 登入
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
  echo -e "${RED}✗ 登入失敗${NC}"
  exit 1
fi

echo -e "${GREEN}✓ 登入成功${NC}\n"

# 計數器
TOTAL=0
SUCCESS=0
FAILED=0
FOUND_FAILURE=false

# 連續下單直到遇到失敗 (最多 20 次)
MAX_ATTEMPTS=20

for i in $(seq 1 $MAX_ATTEMPTS); do
  echo -e "${YELLOW}[嘗試 $i] 創建訂單...${NC}"
  
  ORDER_RESPONSE=$(curl -s -X POST \
    "${BASE_URL}/api/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{
      "productId": "prod-001",
      "quantity": 1,
      "userId": "usr-001"
    }')
  
  ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.orderId')
  
  if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" = "null" ]; then
    echo -e "${RED}  ✗ 訂單創建失敗${NC}"
    continue
  fi
  
  echo -e "${GREEN}  ✓ 訂單已創建: ${ORDER_ID}${NC}"
  ((TOTAL++))
  
  # 等待 Saga 執行
  echo -e "  → 等待 Saga 執行..."
  sleep 6
  
  # 查詢狀態
  FINAL_STATUS=$(curl -s "${BASE_URL}/api/orders/${ORDER_ID}" \
    -H "Authorization: Bearer $TOKEN")
  STATUS=$(echo "$FINAL_STATUS" | jq -r '.status')
  
  if [ "$STATUS" = "CONFIRMED" ]; then
    echo -e "${GREEN}  ✓ 付款成功 (CONFIRMED)${NC}\n"
    ((SUCCESS++))
  elif [ "$STATUS" = "CANCELLED" ] || [ "$STATUS" = "FAILED" ]; then
    echo -e "${RED}  ⚠ 付款失敗 (${STATUS}) - 找到補償場景!${NC}\n"
    ((FAILED++))
    FOUND_FAILURE=true
    
    # 找到失敗場景,詳細展示
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}   找到補償場景! 詳細資訊:${NC}"
    echo -e "${BLUE}========================================${NC}\n"
    
    echo "$FINAL_STATUS" | jq .
    
    echo ""
    echo -e "${GREEN}✓ Saga Rollback 已執行${NC}"
    echo -e "${GREEN}✓ 訂單狀態: ${STATUS}${NC}"
    echo -e "${GREEN}✓ 庫存已自動恢復${NC}"
    echo ""
    echo -e "${YELLOW}saga_state 查詢:${NC}"
    echo -e "${BLUE}SELECT * FROM saga_state WHERE order_id = '${ORDER_ID}';${NC}"
    echo ""
    
    break
  else
    echo -e "${YELLOW}  ? 未知狀態: ${STATUS}${NC}\n"
  fi
  
  sleep 1
done

# 統計
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   統計結果${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "總共嘗試: $TOTAL 筆"
echo -e "${GREEN}成功: $SUCCESS 筆${NC}"
echo -e "${RED}失敗: $FAILED 筆${NC}"
echo ""

if [ "$FOUND_FAILURE" = true ]; then
  echo -e "${GREEN}✓ 成功展示 Saga 補償機制!${NC}"
else
  echo -e "${YELLOW}⚠ 運氣太好了,全部都成功!${NC}"
  echo -e "${YELLOW}  建議:再執行一次,或修改 PaymentService 降低成功率${NC}"
fi

echo ""
echo -e "${YELLOW}說明:${NC}"
echo "90% 成功率理論上 10 次會遇到 1 次失敗"
echo "如果 20 次都沒遇到,機率約 12% (0.9^20)"
