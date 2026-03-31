# 🛒 momo-microservices

> **電商微服務系統 — Staff Software Engineer Side Project**
>
> 針對電商高並發訂單、分散式事務、物流成本優化設計
>
> `Spring Boot 3` · `Kafka` · `Redis` · `MySQL` · `Keycloak` · `K8s` · `ArgoCD` · `Prometheus` · `Jaeger` · `ELK`

---

## 🎯 為什麼做這個專案

momo 電商面對三個核心技術挑戰：

1. **雙十一高並發** — 百萬用戶同時搶購，庫存不能超賣
2. **分散式事務** — 付款失敗時，訂單、庫存、出貨單要一致性回滾
3. **物流成本** — 跨區配送運費高、商品報廢、包裹浪費是主要支出

這個專案針對這三個問題設計解法，並完整部署到 K8s。

---

## 🏗️ 系統架構

```
                         ┌─────────────────────────────────────┐
                         │           Client / Browser           │
                         └─────────────────┬───────────────────┘
                                           │
                         ┌─────────────────▼───────────────────┐
                         │          Nginx API Gateway           │
                         │     限流 10 req/s · JWT 路由         │
                         └──┬────────┬────────┬────────┬────────┘
                            │        │        │        │
               ┌────────────▼──┐ ┌───▼────┐ ┌▼──────┐ ┌▼─────────────┐
               │ user-service  │ │ order  │ │payment│ │  shipping    │
               │   :8081       │ │ :8082  │ │ :8084 │ │   :8083      │
               │               │ │        │ │       │ │              │
               │  Keycloak JWT │ │ Saga   │ │ 90%   │ │  @Kafka      │
               │  驗證 · RBAC  │ │ Redis  │ │ 成功率│ │  Listener    │
               └───────────────┘ └───┬────┘ └───────┘ └──────▲───────┘
                                     │  Kafka: order.confirmed │
                                     └────────────────────────┘

               ┌─────────────────────────────────────────────────────┐
               │                  物流微服務層                        │
               │                                                     │
               │  ┌──────────────────┐    ┌──────────────────┐      │
               │  │ inventory-service │    │ box-calculation  │      │
               │  │     :8085         │    │   service :8086  │      │
               │  │                  │    │                  │      │
               │  │ 分倉路由·FEFO    │    │  3D 裝箱計算     │      │
               │  │ 預測性撥貨排程   │    │  最小紙箱選擇    │      │
               │  └──────────────────┘    └──────────────────┘      │
               └─────────────────────────────────────────────────────┘

               ┌─────────────────────────────────────────────────────┐
               │                   共用基礎設施                       │
               │  Keycloak · Redis · Kafka · MySQL × 5               │
               └─────────────────────────────────────────────────────┘

               ┌─────────────────────────────────────────────────────┐
               │                  可觀測性堆疊                        │
               │  Prometheus · Grafana · ELK · Jaeger                │
               └─────────────────────────────────────────────────────┘
```

---

## 📋 服務清單

| 服務 | Port | 核心功能 | 技術亮點 |
|---|---|---|---|
| **user-service** | 8081 | 會員管理、JWT 驗證 | Keycloak OAuth2 Resource Server |
| **order-service** | 8082 | 下單、庫存、Saga 協調者 | Redis Lua Script、SagaOrchestrator、智慧併單 |
| **payment-service** | 8084 | 付款處理、退款 | Saga Step、冪等設計、forceFail Demo 開關 |
| **shipping-service** | 8083 | 出貨追蹤 | @RetryableTopic DLQ、Saga 補償 Producer |
| **inventory-service** | 8085 | 分倉庫存、FEFO 批號 | Haversine 距離路由、預測性撥貨排程 |
| **box-calculation-service** | 8086 | 3D 裝箱計算 | 最小合適紙箱演算法、材積優化 |

---

## 🔑 核心技術設計

### 1. Orchestration Saga — 分散式事務

付款失敗時，確保訂單、庫存、出貨單一致性回滾。

```
下單
 │
 ▼
SagaOrchestrator（order-service）
 │
 ├─ Step 1: payment.process → payment-service 付款
 │               │
 │         付款成功？
 │         ├── 否 → 補償（逆序）
 │         │         ⑤ 取消出貨單
 │         │         ③ 訂單改 CANCELLED
 │         │         ② 還原 MySQL 庫存
 │         │         ① 還原 Redis 庫存
 │         │         💰 退款
 │         └── 是
 │               │
 └─ Step 2: order.confirmed → shipping-service 出貨 ✅
```

**為什麼選 Orchestration 而不是 Choreography？**

4 個服務、5 個步驟，Choreography 的補償事件會散在各服務難以追蹤。Orchestration 集中管理 SagaState，crash 後可查表恢復，流程可見性高。

```java
// SagaState 記錄每個訂單的 Saga 狀態
public enum SagaStatus {
    STARTED, PAYMENT_REQUESTED, PAYMENT_SUCCESS,
    PAYMENT_FAILED, SHIPPING_REQUESTED,
    COMPLETED, COMPENSATING, COMPENSATED, FAILED
}
```

---

### 2. Redis Lua Script — 防超賣

Lua Script 在 Redis 單線程執行，保證原子性，100 萬並發也不超賣。

```lua
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then return -1 end      -- key 不存在
if stock < tonumber(ARGV[1]) then return -2 end  -- 庫存不足
return redis.call('DECRBY', KEYS[1], ARGV[1])    -- 原子扣減
```

返回值語意：
- `>= 0`：扣減成功，返回剩餘數量
- `-1`：商品不存在
- `-2`：庫存不足（409 給客戶）

---

### 3. DLQ + @RetryableTopic — Kafka 容錯

shipping-service 處理失敗時，自動重試 3 次（指數退避），耗盡後送 DLQ，觸發 Saga 補償。

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0),  // 1s → 2s → 4s
    dltTopicSuffix = ".DLQ"
)
@KafkaListener(topics = "order.confirmed")
public void handleOrderConfirmed(OrderConfirmedEvent event) { ... }

@DltHandler
public void handleDlt(OrderConfirmedEvent event) {
    // 重試耗盡 → 觸發 Saga 補償
    compensationProducer.sendCancelEvent(event, "出貨失敗，重試耗盡");
}
```

---

### 4. 分倉路由（Haversine + Redis）

客戶下單時，選最近有貨的倉庫，降低跨區配送運費。

```
客戶地址（緯經度）
       │
       ▼
Haversine 公式計算各倉距離
       │
       ▼
由近到遠逐一查 Redis 庫存（O(1)）
       │
       ▼
Redis Lua Script 原子扣減
       │
       ▼
MySQL 同步扣減（最終一致性）
       │
       ▼
FEFO 指派效期最短批號給撿貨員
```

```sql
-- FEFO 核心查詢：效期最短的批號優先出貨
SELECT b FROM BatchRecord b
WHERE b.productId = :productId
  AND b.warehouseId = :warehouseId
  AND b.quantity > 0
ORDER BY
  CASE WHEN b.expireDate IS NULL THEN 1 ELSE 0 END,
  b.expireDate ASC   -- 越早到期排越前面
```

---

### 5. 智慧併單（Order Merging）

30 分鐘內同客戶同倉庫訂單合成一個包裹，節省運費。

```java
// 查詢可合併的訂單
List<OrderEntity> mergeable = orderRepository
    .findMergeableOrders(userId, warehouseId, windowStart, newOrderId);
// 條件：同 userId + 同倉庫 + 30 分鐘內 + PENDING 狀態
```

---

### 6. 3D 裝箱計算

計算最小合適紙箱，減少材積運費浪費。

```
商品總體積 × 1.2（緩衝）
        │
        ▼
從 XS → XXL 找第一個能裝下的標準箱
        │
        ▼
回傳：紙箱尺寸 + 空間使用率 + 是否需要拆包裝
```

標準紙箱：XS(20×15×10) · S(30×20×15) · M(40×30×20) · L(50×40×30) · XL(60×50×40) · XXL(80×60×50)

---

## 🚀 快速啟動

### 前置需求

- Java 17+
- Docker Desktop（已啟用 K8s）
- Maven 3.8+

### 方式一：docker-compose（本地開發）

```bash
# Step 1: Build 所有 JAR
for svc in user-service order-service shipping-service payment-service inventory-service box-calculation-service; do
  cd $svc && mvn clean package -DskipTests && cd ..
done

# Step 2: 啟動全部
docker-compose up --build

# 服務啟動需要約 60 秒（等 Keycloak 和 MySQL 就緒）
```

**啟動的 Container：**

| Container | Port | 說明 |
|---|---|---|
| nginx | 80 | API Gateway |
| keycloak | 8080 | SSO |
| user-service | 8081 | 會員服務 |
| order-service | 8082 | 訂單服務 |
| payment-service | 8084 | 付款服務 |
| shipping-service | 8083 | 出貨服務 |
| inventory-service | 8085 | 庫存路由 |
| box-calculation-service | 8086 | 裝箱計算 |
| mysql-user | 3307 | 會員 DB |
| mysql-order | 3308 | 訂單 DB |
| mysql-shipping | 3309 | 出貨 DB |
| mysql-payment | 3310 | 付款 DB |
| mysql-inventory | 3311 | 庫存 DB |
| kafka | 9092 | 訊息中介 |
| redis | 6379 | 庫存快取 |

### 方式二：K8s 一鍵部署

```bash
# 替換 GitHub 帳號後執行
bash deploy.sh 你的GitHub帳號 你的GitHub_Token
```

腳本自動完成：Build → Push image → 建立 K8s Secret → 部署服務 → 安裝 ArgoCD → 部署 Observability

---

## 🔑 取得 JWT Token

```bash
curl -X POST http://localhost:8080/realms/momo-realm/protocol/openid-connect/token \
  -d "client_id=momo-client" \
  -d "client_secret=momo-client-secret" \
  -d "username=testuser" \
  -d "password=password" \
  -d "grant_type=password" | jq .access_token
```

測試帳號：

| 帳號 | 密碼 | 角色 |
|---|---|---|
| testuser | password | user |
| admin-user | admin123 | user, admin |

---

## 📡 主要 API

### 下單

```bash
POST http://localhost/api/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": "usr-001",
  "productId": "prod-001",
  "quantity": 1
}
```

### 查詢付款狀態

```bash
GET http://localhost/api/payments/order/{orderId}
Authorization: Bearer <token>
```

### 分倉路由（指定客戶座標）

```bash
POST http://localhost/api/inventory/allocate
Authorization: Bearer <token>

{
  "productId": "prod-001",
  "quantity": 1,
  "userLat": 25.033,
  "userLng": 121.565
}
```

### 3D 裝箱計算

```bash
POST http://localhost/api/box/calculate

{
  "items": [
    {
      "productId": "prod-001",
      "quantity": 1,
      "length": 16,
      "width": 7.7,
      "height": 0.7,
      "weightKg": 0.2
    }
  ]
}
```

### 觸發 Saga 補償 Demo（強制付款失敗）

```bash
# 開啟強制失敗
POST http://localhost/api/payments/demo/force-fail/true

# 下單後付款失敗，觀察 Saga 補償觸發
POST http://localhost/api/orders ...

# 關閉強制失敗
POST http://localhost/api/payments/demo/force-fail/false
```

---

## 📊 可觀測性（Observability）

```bash
# Grafana（Metrics 視覺化）
kubectl port-forward svc/grafana 3000:3000 -n momo-system
# http://localhost:3000  admin/admin

# Kibana（Log 查詢）
kubectl port-forward svc/kibana 5601:5601 -n momo-system
# http://localhost:5601

# Jaeger（Distributed Tracing）
kubectl port-forward svc/jaeger 16686:16686 -n momo-system
# http://localhost:16686
```

**三大支柱：**

| 支柱 | 工具 | 說明 |
|---|---|---|
| Metrics | Prometheus + Grafana | HTTP 請求數、JVM 記憶體、Kafka Lag |
| Logs | Filebeat + Logstash + Elasticsearch + Kibana | JSON 結構化 Log，含 traceId |
| Tracing | OpenTelemetry + Jaeger | 跨服務請求鏈追蹤 |

Kibana 查詢範例：
```
# 查特定訂單的所有 Log（跨服務）
traceId: "abc123"

# 查付款失敗的 Log
service: "payment-service" AND level: "ERROR"
```

---

## 🔄 CI/CD Pipeline

```
git push origin main
       │
       ▼
GitHub Actions
  detect-changes Job
  ├── 只有改 order-service/ → 只 build order-service
  ├── 多個服務同時改 → 多 Job 並行
  └── 只改 k8s/ → 不 build，直接 ArgoCD sync
       │
       ▼
  build Job
  ① mvn test
  ② docker build
  ③ push → ghcr.io/帳號/order-service:abc1234
  ④ 更新 k8s/base/order-service.yaml image tag
  ⑤ git commit + push 回 repo
       │
       ▼
ArgoCD 偵測 yaml 變更
       │
       ▼
K8s Rolling Update（零停機）
  maxSurge: 1         # 先長出新 Pod
  maxUnavailable: 0   # 舊 Pod 等新 Pod ready 才砍
```

---

## ☸️ K8s 架構

```
momo-system namespace
├── user-service        Deployment (2-10 pods) + HPA
├── order-service       Deployment (2-20 pods) + HPA  ← 閃購主力
├── payment-service     Deployment (2-10 pods) + HPA
├── shipping-service    Deployment (2-10 pods) + HPA
├── inventory-service   Deployment (2-10 pods) + HPA
├── box-calculation-service  Deployment (2 pods)
├── mysql-user/order/shipping/payment/inventory  各自 PVC
├── redis               StatefulSet
├── kafka               StatefulSet
└── keycloak            Deployment

argocd namespace
└── App of Apps → 管理 momo-system 所有服務

observability（也在 momo-system）
├── prometheus
├── grafana
├── elasticsearch + logstash + kibana
├── filebeat           DaemonSet（每個 Node 收 Log）
└── jaeger
```

HPA 雙十一擴容示意（order-service）：
```
平時：  2 pods
流量來：2 → 4 → 8 → 12 → 16 → 20 pods
流量退：20 → 8 → 2 pods（stabilizationWindow 300s）
```

---

## 📁 專案結構

```
momo-microservices/
├── user-service/
│   └── src/main/java/com/demo/user/
│       ├── controller/     UserController
│       ├── service/        UserService
│       ├── model/          UserEntity
│       ├── repository/     UserRepository
│       └── config/         SecurityConfig（Keycloak JWT）
│
├── order-service/
│   └── src/main/java/com/demo/order/
│       ├── controller/     OrderController
│       ├── service/        OrderService · StockRedisService · OrderMergeService
│       ├── saga/           SagaOrchestrator · SagaState · SagaStateRepository
│       ├── kafka/          OrderKafkaProducer · OrderSagaConsumer
│       ├── client/         UserServiceClient（跨服務 REST）
│       └── config/         RedisConfig · KafkaTopicConfig
│
├── payment-service/
│   └── src/main/java/com/demo/payment/
│       ├── service/        PaymentService（90% 成功率模擬）
│       ├── kafka/          PaymentKafkaConsumer
│       └── controller/     PaymentDemoController（forceFail 開關）
│
├── shipping-service/
│   └── src/main/java/com/demo/shipping/
│       ├── kafka/          ShippingKafkaConsumer（@RetryableTopic + @DltHandler）
│       └── kafka/          ShippingCompensationProducer（Saga 補償）
│
├── inventory-service/
│   └── src/main/java/com/demo/inventory/
│       ├── service/        InventoryRouterService（Haversine + FEFO）
│       ├── repository/     BatchRecordRepository（FEFO 查詢）
│       ├── repository/     WarehouseRepository（Haversine SQL）
│       └── scheduler/      ReplenishmentScheduler（預測性撥貨）
│
├── box-calculation-service/
│   └── src/main/java/com/demo/box/
│       └── service/        BoxCalculationService（3D 裝箱演算法）
│
├── k8s/
│   ├── base/               六個服務 Deployment + Service + HPA + MySQL
│   └── observability/      Prometheus + Grafana + ELK + Jaeger
│
├── argocd/
│   ├── app-of-apps.yaml    App of Apps pattern
│   └── apps/               四個子 App
│
├── .github/workflows/
│   └── ci.yml              七個 Job（detect-changes + 六個 build）
│
├── nginx/nginx.conf         API Gateway + 限流
├── keycloak/realm-export.json
├── sql/                    五個服務的 init SQL
├── docker-compose.yml
└── deploy.sh               一鍵 K8s 部署腳本
```

---

## 📐 DDD 領域驅動設計（order-service）

### 三種概念對應業務

| 概念 | 本專案 | 說明 |
|---|---|---|
| **Aggregate Root** | `Order` | 訂單入口，外部只能透過它的方法操作 |
| **Entity** | `OrderItem` | 有唯一 itemId，需要追蹤生命週期 |
| **Value Object** | `Money` | 無 ID，用值描述，不可變，統一精度 |

### Service 層變薄，業務邏輯下沉

```java
// ❌ 沒有 DDD：業務規則散在 Service，語意不清晰
order.setStatus("CANCELLED");  // 已出貨也能被取消！
order.setUpdatedAt(LocalDateTime.now());

// ✅ 有 DDD：業務規則集中在 Domain，Service 只協調流程
order.cancel("Saga 補償：付款失敗");
// cancel() 內部保護：已出貨不能取消
```

### Money Value Object — 解決精度問題

```java
// ❌ BigDecimal 到處散落，精度不一致
BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));

// ✅ Money 統一精度（scale=2, HALF_UP），語意清晰
Money total = unitPrice.multiply(quantity);
```

### 與信用卡清算系統的對比

在 Worldline 的信用卡清算系統同樣的設計：

| 本專案 | 信用卡清算（Worldline） |
|---|---|
| `Order`（Aggregate Root） | `CardAccount`（Aggregate Root） |
| `Money`（Value Object） | `CreditLimit`、`MonetaryAmount` |
| `OrderItem`（Entity） | `Transaction`（Entity） |
| `order.cancel()` | `cardAccount.authorize()` |

**核心原則相同：業務規則集中在 Domain，Service 只協調流程。**

---

## 🛡️ Resilience4j 容錯防護

五個模組，覆蓋整條請求鏈：

```
client 下單
    │
    ▼
Nginx（限流 10 req/s）
    │
    ▼
order-service
    ├── 呼叫 user-service
    │   ├── @Bulkhead      最多 10 Thread 同時呼叫，隔離故障範圍
    │   ├── @CircuitBreaker 失敗率 50% 熔斷，OPEN → HALF-OPEN → CLOSED
    │   └── @Retry         失敗重試 3 次（500ms → 1s → 2s 指數退避）
    │
    └── 呼叫 payment-service（透過 Kafka + Saga）
        └── @TimeLimiter   超過 5 秒 Fail-Fast → 觸發 Saga 補償

inventory-service
    └── /allocate
        └── @RateLimiter   每秒最多 50 請求，超過回傳 429
```

### 設計決策：Fail-Open vs Fail-Fast

| 服務 | 策略 | 理由 |
|---|---|---|
| user-service fallback | **Fail-Open**（暫時允許通過） | 用戶驗證低風險，寧可短暫允許也不讓所有訂單失敗 |
| payment-service timeout | **Fail-Fast**（直接拒絕） | 付款高風險，金額不能處於不確定狀態，明確失敗觸發退款 |

### 各模組位置

| 模組 | 位置 | 參數 |
|---|---|---|
| Circuit Breaker | `order-service/UserServiceClient` | 滑動窗口 10 次，失敗率 50% 熔斷，等待 30s |
| Retry | `order-service/UserServiceClient` | 最多 3 次，指數退避 500ms→1s→2s |
| Bulkhead | `order-service/UserServiceClient` | 最多 10 Thread，等待佇列 100ms |
| Rate Limiter | `inventory-service/InventoryController` | 每秒 50 請求，超過立即 429 |
| TimeLimiter | `payment-service/PaymentService` | 超過 5 秒 Fail-Fast，觸發 Saga 補償 |

---

## 🏷️ 技術選型說明

| 決策 | 選擇 | 理由 |
|---|---|---|
| Saga 模式 | Orchestration | 4+ 服務流程複雜，需要集中管理狀態和補償邏輯 |
| 庫存防超賣 | Redis Lua Script | 原子操作，比 DB 樂觀鎖效能高，天然防並發 |
| 訊息容錯 | @RetryableTopic + DLQ | Spring Kafka 原生支援，不需要額外框架 |
| 分倉路由 | Haversine + Redis | 球面距離公式精確，Redis O(1) 查詢快 |
| 服務容錯 | Resilience4j 五模組 | Circuit Breaker / Retry / Bulkhead / RateLimiter / TimeLimiter 覆蓋所有故障場景 |
| CI/CD | GitHub Actions + ArgoCD | 業界標準 GitOps 組合，免費且易整合 |
| 可觀測性 | Prometheus + ELK + Jaeger | Metrics、Log、Tracing 三大支柱完整覆蓋 |

---

## 🔗 相關連結

- **Demo 前端（模擬版）**：`momo-demo-frontend.html`
- **Demo 前端（AI Mock API 版）**：`momo-demo-frontend-ai.html`
- **Demo 前端（真實 API 版）**：`momo-demo-frontend-real.html`
- **Swagger UI**：`http://localhost:808x/swagger-ui.html`（各服務）
