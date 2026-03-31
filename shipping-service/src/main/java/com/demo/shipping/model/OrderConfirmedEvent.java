package com.demo.shipping.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接收來自 order-service 的 Kafka 事件
 * 必須跟 order-service 的 OrderConfirmedEvent 欄位一致
 */
@Data
public class OrderConfirmedEvent {
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private LocalDateTime confirmedAt;
}
