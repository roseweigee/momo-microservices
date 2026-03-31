package com.demo.shipping.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelEvent {
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
    private String reason;
    private LocalDateTime cancelledAt;
}
