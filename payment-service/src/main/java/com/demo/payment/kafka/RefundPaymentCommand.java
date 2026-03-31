package com.demo.payment.kafka;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RefundPaymentCommand {
    private String orderId;
    private String paymentId;
    private String reason;
    private LocalDateTime requestedAt;
}
