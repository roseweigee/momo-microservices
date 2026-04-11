package com.demo.payment.kafka;

import com.demo.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "payment.process", groupId = "payment-group")
    public void handleProcessPayment(@Payload String payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Map<String, Object> cmd = mapper.readValue(payload, Map.class);
        String orderId = (String) cmd.get("orderId");
        String userId = (String) cmd.get("userId");
        BigDecimal amount = new BigDecimal(cmd.get("amount").toString());
        log.info("收到付款指令：orderId={}, amount={}", orderId, amount);
        paymentService.processPayment(orderId, userId, amount);
    }

    @KafkaListener(topics = "payment.refund", groupId = "payment-group")
    public void handleRefundPayment(@Payload String payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> cmd = mapper.readValue(payload, Map.class);
        String orderId = (String) cmd.get("orderId");
        String reason = (String) cmd.get("reason");
        log.info("收到退款指令：orderId={}, reason={}", orderId, reason);
        paymentService.refundPayment(orderId, reason);
    }
}
