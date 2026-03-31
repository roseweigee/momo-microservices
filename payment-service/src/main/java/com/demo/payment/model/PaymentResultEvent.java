package com.demo.payment.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentResultEvent {
    private String orderId;
    private String paymentId;
    private boolean success;
    private String failureReason;
    private LocalDateTime processedAt;
}
