package com.demo.order.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aggregate Root：訂單
 *
 * DDD 核心原則：
 * 1. 業務規則集中在這裡，不散落在 Service 層
 * 2. 外部只能透過 Order 的方法操作，不能直接 setStatus()
 * 3. 狀態轉換有明確的業務語意（confirm、cancel、ship）
 *
 * 對比沒有 DDD 的寫法：
 *   // Service 層直接 set，業務語意不清晰
 *   order.setStatus(OrderStatus.CANCELLED);
 *   order.setUpdatedAt(LocalDateTime.now());
 *
 * 有 DDD 的寫法：
 *   // 業務語意明確，且規則集中
 *   order.cancel("庫存不足");
 *
 * 這個 Aggregate 包含：
 * - OrderId（Identity）
 * - Money（Value Object）— 封裝金額計算
 * - OrderStatus（Value Object）— 封裝狀態轉換規則
 * - 業務方法：confirm()、cancel()、ship()
 */
@Slf4j
@Getter
public class Order {

    // ── Identity ──────────────────────────────
    private final String orderId;

    // ── 基本屬性 ──────────────────────────────
    private final String userId;
    private final String productId;
    private final int quantity;

    // ── Value Object：金額 ────────────────────
    private final Money unitPrice;
    private final Money totalPrice;  // = unitPrice × quantity

    // ── 狀態 ──────────────────────────────────
    private OrderStatus status;
    private String cancelReason;

    // ── 時間戳記 ──────────────────────────────
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 建立新訂單（Factory Method）
     *
     * 為什麼用 Factory Method 而不是 constructor？
     * 1. 語意清晰：Order.create() 明確表示「建立」動作
     * 2. 可以封裝 orderId 生成邏輯
     * 3. 確保初始狀態一定是 PENDING，不允許外部指定
     */
    public static Order create(String userId, String productId,
                                int quantity, Money unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("訂單數量必須大於 0");
        }

        return new Order(
            "ord-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            userId,
            productId,
            quantity,
            unitPrice,
            unitPrice.multiply(quantity),  // 總金額由 Domain 計算，不由外部傳入
            OrderStatus.PENDING,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private Order(String orderId, String userId, String productId,
                  int quantity, Money unitPrice, Money totalPrice,
                  OrderStatus status, String cancelReason,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.status = status;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── 業務方法 ──────────────────────────────

    /**
     * 確認訂單（付款成功後）
     *
     * 業務規則：只有 PENDING 的訂單可以確認
     * 這個規則集中在這裡，不需要在每個 Service 裡重複寫
     */
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                "訂單狀態不允許確認，當前狀態：" + this.status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
        log.info("訂單確認：orderId={}", orderId);
    }

    /**
     * 取消訂單（Saga 補償時呼叫）
     *
     * 業務規則：
     * 1. 已出貨的訂單不能取消（需走退貨流程）
     * 2. 取消原因必須記錄（金融合規要求）
     */
    public void cancel(String reason) {
        if (this.status == OrderStatus.SHIPPED ||
            this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                "已出貨或已送達的訂單不能直接取消，請走退貨流程");
        }
        if (this.status == OrderStatus.CANCELLED) {
            log.warn("訂單已是取消狀態，忽略重複取消：orderId={}", orderId);
            return;  // 冪等處理：重複取消不報錯
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = LocalDateTime.now();
        log.info("訂單取消：orderId={}, reason={}", orderId, reason);
    }

    /**
     * 標記出貨
     */
    public void ship() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                "只有已確認的訂單可以出貨，當前狀態：" + this.status);
        }
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = LocalDateTime.now();
        log.info("訂單出貨：orderId={}", orderId);
    }

    /** 查詢是否可以取消 */
    public boolean isCancellable() {
        return this.status == OrderStatus.PENDING ||
               this.status == OrderStatus.CONFIRMED;
    }

    // ── 訂單狀態（Value Object） ───────────────
    public enum OrderStatus {
        PENDING,    // 待付款
        CONFIRMED,  // 已付款確認
        SHIPPED,    // 已出貨
        DELIVERED,  // 已送達
        CANCELLED   // 已取消
    }
}
