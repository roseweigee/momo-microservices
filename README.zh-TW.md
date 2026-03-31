# 🛒 momo-microservices

> **電商微服務系統**  
> Spring Boot 3 · Kafka · Redis · MySQL · Keycloak SSO · Nginx API Gateway · K8s · ArgoCD

---

## 📐 系統架構

```
                    ┌──────────────────────────────┐
                    │         Client Request        │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │      Nginx API Gateway        │
                    │  • 限流：10 req/s per IP       │
                    │  • 路由到各微服務              │
                    └───┬──────────┬───────────┬───┘
                        │          │           │
            ┌───────────▼─┐  ┌─────▼──────┐  ┌▼──────────────┐
            │ user-service │  │order-service│  │shipping-service│
            │   Port 8081  │  │  Port 8082  │  │   Port 8083    │
            │              │  │             │  │                │
            │  userdb      │  │  orderdb    │  │  shippingdb    │
            │  (MySQL)     │  │  (MySQL)    │  │  (MySQL)       │
            └─────────────┘  └──────┬──────┘  └───────▲────────┘
                                    │                   │
                               Kafka │ order.confirmed   │
                                    └───────────────────┘

    共用基礎設施：
    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
    │  Keycloak   │  │    Redis    │  │    Kafka    │
    │ Port 8080   │  │  Port 6379  │  │  Port 9092  │
    │ JWT 簽發     │  │ 庫存快取    │  │ 跨服務事件  │
    └─────────────┘  └─────────────┘  └─────────────┘
```

---

## 🔄 完整下單流程

```
① Client → Nginx → order-service
   POST /api/orders

② order-service 呼叫 user-service
   驗證 userId 是否存在（REST 同步）

③ Redis Lua Script 原子扣減庫存
   防止超賣

④ MySQL 扣減庫存 + 建立訂單
   status = CONFIRMED

⑤ Kafka Producer 發送事件
   topic: order.confirmed

⑥ shipping-service Consumer 收到事件
   自動建立出貨單
   status = PREPARING
```

---

## 🛠 技術架構

| 層級 | 技術 | 用途 |
|---|---|---|
| **閘道層** | Nginx | 限流、路由到各服務 |
| **認證層** | Keycloak 23 + Spring Security | SSO、JWT 驗證、角色控管 |
| **會員服務** | user-service (Spring Boot) | 會員資料管理 |
| **訂單服務** | order-service (Spring Boot) | 下單、庫存、Kafka Producer |
| **出貨服務** | shipping-service (Spring Boot) | Kafka Consumer、出貨追蹤 |
| **快取層** | Redis + Lua Script | 原子庫存扣減 |
| **訊息層** | Apache Kafka | 跨服務非同步事件 |
| **資料層** | MySQL × 3（各服務獨立 DB） | 微服務 DB 隔離 |
| **部署** | Docker Compose / K8s + ArgoCD | 容器化與 GitOps |

---

## 🚀 快速啟動

### 前置需求
- Java 17+、Docker Desktop、Maven 3.8+

### 第一步：Build 所有服務

```bash
cd user-service    && mvn clean package -DskipTests && cd ..
cd order-service   && mvn clean package -DskipTests && cd ..
cd shipping-service && mvn clean package -DskipTests && cd ..
```

### 第二步：啟動所有服務

```bash
docker-compose up --build
```

共啟動 **10 個 container**：

| Container | Port | 說明 |
|---|---|---|
| `nginx` | 80 | API Gateway |
| `keycloak` | 8080 | SSO |
| `user-service` | 8081 | 會員服務 |
| `order-service` | 8082 | 訂單服務 |
| `shipping-service` | 8083 | 出貨服務 |
| `mysql-user` | 3307 | 會員 DB |
| `mysql-order` | 3308 | 訂單 DB |
| `mysql-shipping` | 3309 | 出貨 DB |
| `kafka` | 9092 | 訊息中介 |
| `redis` | 6379 | 庫存快取 |

---

## 🔑 取得 Token

```bash
curl -X POST http://localhost:8080/realms/momo-realm/protocol/openid-connect/token \
  -d "client_id=momo-client" \
  -d "client_secret=momo-client-secret" \
  -d "username=testuser" \
  -d "password=password" \
  -d "grant_type=password"
```

---

## 📡 API 說明

### User Service（Port 8081）

```bash
# 查詢會員
GET http://localhost/api/users/{userId}
Authorization: Bearer <token>
```

### Order Service（Port 8082）

```bash
# 建立訂單
POST http://localhost/api/orders
Authorization: Bearer <token>
{
  "userId":    "usr-001",
  "productId": "prod-001",
  "quantity":  2
}

# 查詢訂單
GET http://localhost/api/orders/{orderId}

# 查詢 Redis 庫存（公開）
GET http://localhost/api/orders/stock/{productId}
```

### Shipping Service（Port 8083）

```bash
# 依訂單查出貨狀態
GET http://localhost/api/shipping/order/{orderId}
Authorization: Bearer <token>

# 更新出貨狀態（admin）
PATCH http://localhost/api/shipping/{shipmentId}/status?status=SHIPPED
```

---

## 👤 測試帳號

| 帳號 | 密碼 | 角色 |
|---|---|---|
| `testuser` | `password` | user |
| `admin-user` | `admin123` | user, admin |

---

## ⚡ 核心設計決策

### 1. 微服務 DB 隔離
每個服務有自己獨立的 MySQL DB，完全不共用 schema。這是微服務最重要的原則之一 — 資料的邊界就是服務的邊界。

### 2. 跨服務通訊策略
- **同步（REST）**：order-service 驗證 user 是否存在 → 需要立即結果
- **非同步（Kafka）**：訂單確認後通知 shipping-service → 不需要等待，解耦

### 3. 冪等設計（Idempotency）
shipping-service 建立出貨單前先檢查 orderId 是否已存在，避免 Kafka 重複消費造成重複出貨。

### 4. Redis Lua 原子防超賣
Lua Script 在 Redis 單執行緒執行，保證高並發下庫存不會超賣。

### 5. Keycloak 統一認證
三個服務都設定為 OAuth2 Resource Server，共用同一個 Keycloak realm，JWT 驗證邏輯不重複實作。

---

## 📦 專案結構

```
momo-microservices/
├── user-service/              # 會員服務 (Port 8081)
│   ├── src/main/java/com/demo/user/
│   │   ├── controller/        # UserController
│   │   ├── service/           # UserService
│   │   ├── model/             # UserEntity
│   │   ├── repository/        # UserRepository
│   │   └── config/            # SecurityConfig
│   └── Dockerfile
├── order-service/             # 訂單服務 (Port 8082)
│   ├── src/main/java/com/demo/order/
│   │   ├── controller/        # OrderController
│   │   ├── service/           # OrderService, StockRedisService
│   │   ├── model/             # OrderEntity, OrderConfirmedEvent
│   │   ├── repository/        # OrderRepository, ProductRepository
│   │   ├── kafka/             # OrderKafkaProducer
│   │   ├── client/            # UserServiceClient (跨服務呼叫)
│   │   └── config/            # AppConfig, RedisConfig
│   └── Dockerfile
├── shipping-service/          # 出貨服務 (Port 8083)
│   ├── src/main/java/com/demo/shipping/
│   │   ├── controller/        # ShippingController
│   │   ├── service/           # ShippingService
│   │   ├── model/             # ShipmentEntity, OrderConfirmedEvent
│   │   ├── repository/        # ShipmentRepository
│   │   ├── kafka/             # ShippingKafkaConsumer
│   │   └── config/            # SecurityConfig
│   └── Dockerfile
├── nginx/
│   └── nginx.conf             # API Gateway + 限流
├── keycloak/
│   └── realm-export.json      # SSO 設定自動匯入
├── sql/
│   ├── user-init.sql
│   ├── order-init.sql
│   └── shipping-init.sql
└── docker-compose.yml
```

---

## 🔄 與單體版本的差異

| 項目 | 單體版本 | 微服務版本 |
|---|---|---|
| **資料庫** | 共用一個 MySQL | 各服務獨立 DB |
| **部署** | 一個 JAR | 三個獨立 JAR |
| **擴展** | 整體擴展 | 可針對單一服務擴展 |
| **跨服務通訊** | 直接 method call | REST + Kafka |
| **複雜度** | 低 | 較高，但更靈活 |

---

## 📝 Production 延伸考量

| 項目 | 目前 | 正式環境建議 |
|---|---|---|
| **服務發現** | Docker DNS | K8s Service / Eureka |
| **跨服務呼叫** | RestTemplate | OpenFeign + Circuit Breaker |
| **分散式追蹤** | 無 | Jaeger / Zipkin |
| **訊息可靠性** | At-least-once | Idempotent Consumer |
| **設定管理** | application.yml | Spring Cloud Config |
| **API Gateway** | Nginx | Spring Cloud Gateway |
