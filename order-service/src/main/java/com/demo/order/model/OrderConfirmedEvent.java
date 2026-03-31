package com.demo.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka 事件：訂單確認後發送給 shipping-service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedEvent {
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private LocalDateTime confirmedAt;
}
