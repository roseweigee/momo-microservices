# 📐 DDD 設計說明 — order-service

## 為什麼引入 DDD？

原本的寫法，業務規則散在 Service 層：

```java
// ❌ 沒有 DDD：業務邏輯在 Service，語意不清晰
public void cancelOrder(String orderId) {
    OrderEntity order = repository.findById(orderId);
    order.setStatus("CANCELLED");       // 直接 set，沒有業務保護
    order.setUpdatedAt(LocalDateTime.now());
    repository.save(order);
    // 問題：已出貨的訂單也能被取消！
}
```

引入 DDD 後，業務規則集中在 Domain 物件：

```java
// ✅ 有 DDD：Service 薄，Domain 厚
public void cancelOrder(String orderId) {
    OrderEntity entity = repository.findById(orderId);
    Order order = mapper.toDomain(entity);
    order.cancel("Saga 補償：付款失敗");  // 業務規則在 Domain 裡
    repository.save(mapper.toEntity(order));
}

// Domain 保護業務規則
public void cancel(String reason) {
    if (status == SHIPPED || status == DELIVERED) {
        throw new IllegalStateException("已出貨不能取消，請走退貨流程");
    }
    // ...
}
```

---

## 三種概念對照

### Aggregate Root — Order（訂單）
```
外部只能透過 Order 的方法操作，不能直接 setStatus()

Order
├── confirm()    付款成功後確認
├── cancel()     Saga 補償時取消（含業務規則保護）
├── ship()       出貨
└── isCancellable()  判斷是否可取消
```

### Entity — OrderItem（訂單明細）
```
有唯一 itemId，需要追蹤生命週期
同樣商品的兩筆明細是不同的 Entity

OrderItem
├── itemId（唯一識別）
├── productId
├── quantity
└── subtotal = unitPrice × quantity
```

### Value Object — Money（金額）
```
沒有 ID，用值描述，不可變
NT$100 == NT$100，不管是哪個物件

Money
├── amount（BigDecimal，統一 scale=2）
├── multiply(int quantity) → 回傳新 Money
└── add(Money other)       → 回傳新 Money
```

---

## 架構分層

```
Controller 層
    │  接收 HTTP 請求，轉換參數
    ▼
Service 層（薄）
    │  協調流程：呼叫 Domain + Repository
    │  不包含業務規則
    ▼
Domain 層（DDD 核心）← 這裡
    │  Order（Aggregate Root）
    │  OrderItem（Entity）
    │  Money（Value Object）
    ▼
Repository 層
    │  存取資料庫（JPA）
    ▼
OrderDomainMapper
    Domain ↔ JPA Entity 互轉
```

---

## 與信用卡清算系統的對比

在 Worldline 的信用卡清算系統，同樣的概念：

| 本專案 | 信用卡清算 |
|---|---|
| Order（Aggregate Root） | CardAccount（Aggregate Root） |
| Money（Value Object） | CreditLimit、MonetaryAmount（Value Object） |
| OrderItem（Entity） | Transaction（Entity） |
| OrderStatus（Enum） | TransactionStatus（Enum） |

核心原則相同：**業務規則集中在 Domain 物件，Service 只協調流程。**
