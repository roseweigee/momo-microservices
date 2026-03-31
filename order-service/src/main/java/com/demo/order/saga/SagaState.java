package com.demo.order.saga;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Saga 狀態表
 * Orchestrator 靠這個知道每個訂單目前走到哪一步
 * 也是分散式事務的狀態管理核心
 */
@Entity
@Table(name = "saga_state")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SagaState {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "failure_reason")
    private String failureReason;

    // 補償時需要知道這些資訊
    @Column(name = "product_id")
    private String productId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "shipment_id")
    private String shipmentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SagaStatus {
        STARTED,            // 開始
        PAYMENT_REQUESTED,  // 已發付款指令，等待結果
        PAYMENT_SUCCESS,    // 付款成功
        PAYMENT_FAILED,     // 付款失敗 → 開始補償
        SHIPPING_REQUESTED, // 已發出貨指令
        COMPLETED,          // 全部完成 ✅
        COMPENSATING,       // 補償中
        COMPENSATED,        // 補償完成（訂單取消）
        FAILED              // 補償也失敗，需人工介入
    }
}
