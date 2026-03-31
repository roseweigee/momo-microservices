package com.demo.payment.kafka;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProcessPaymentCommand {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private LocalDateTime requestedAt;
}
