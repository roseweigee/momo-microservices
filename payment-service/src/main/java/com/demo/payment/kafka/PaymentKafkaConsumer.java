package com.demo.payment.kafka;

import com.demo.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "payment.process", groupId = "payment-group")
    public void handleProcessPayment(@Payload ProcessPaymentCommand cmd) {
        log.info("收到付款指令：orderId={}, amount={}", cmd.getOrderId(), cmd.getAmount());
        paymentService.processPayment(cmd.getOrderId(), cmd.getUserId(), cmd.getAmount());
    }

    @KafkaListener(topics = "payment.refund", groupId = "payment-group")
    public void handleRefundPayment(@Payload RefundPaymentCommand cmd) {
        log.info("收到退款指令：orderId={}, reason={}", cmd.getOrderId(), cmd.getReason());
        paymentService.refundPayment(cmd.getOrderId(), cmd.getReason());
    }
}
