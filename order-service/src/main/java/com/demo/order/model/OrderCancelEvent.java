package com.demo.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Saga 補償事件
 * shipping-service 處理失敗時發送此事件
 * order-service 收到後取消訂單、還原庫存
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelEvent {
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
    private String reason;          // 取消原因
    private LocalDateTime cancelledAt;
}
