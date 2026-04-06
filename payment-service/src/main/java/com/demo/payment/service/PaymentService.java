package com.demo.payment.service;

import com.demo.payment.controller.PaymentDemoController;
import com.demo.payment.model.PaymentEntity;
import com.demo.payment.model.PaymentResultEvent;
import com.demo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentResultEvent> kafkaTemplate;

    private static final String PAYMENT_RESULT_TOPIC = "payment.result";

    @Transactional
    public void processPayment(String orderId, String userId, BigDecimal amount) {

        // 冪等保護：同一個 orderId 不重複付款
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Payment already processed for orderId={}", orderId);
            return;
        }

        String paymentId = UUID.randomUUID().toString();
        boolean success = simulatePayment(amount);

        PaymentEntity payment = PaymentEntity.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .status(success ? PaymentEntity.PaymentStatus.SUCCESS
                        : PaymentEntity.PaymentStatus.FAILED)
                .failureReason(success ? null : "模擬付款失敗：餘額不足")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        log.info("Payment {}: orderId={}, amount={}, forceFail={}",
                success ? "SUCCESS" : "FAILED", orderId, amount,
                PaymentDemoController.FORCE_FAIL.get());

        // 發付款結果給 Orchestrator
        kafkaTemplate.send(PAYMENT_RESULT_TOPIC, orderId,
                PaymentResultEvent.builder()
                        .orderId(orderId)
                        .paymentId(paymentId)
                        .success(success)
                        .failureReason(payment.getFailureReason())
                        .processedAt(LocalDateTime.now())
                        .build());
    }

    @Transactional
    public void refundPayment(String orderId, String reason) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentEntity.PaymentStatus.REFUNDED);
            payment.setFailureReason(reason);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            log.info("Payment refunded: orderId={}", orderId);
        });
    }

    /**
     * 模擬付款：可透過 API 動態切換失敗模式
     *
     * Demo 控制:
     * - POST /api/payments/demo/force-fail/true  → 強制失敗
     * - POST /api/payments/demo/force-fail/false → 正常流程 (90% 成功率)
     *
     * 生產環境改為呼叫真實金流 API（綠界、藍新等）
     */
    private boolean simulatePayment(BigDecimal amount) {
        // 檢查是否開啟「強制失敗」模式
        if (PaymentDemoController.FORCE_FAIL.get()) {
            log.warn("⚠️ FORCE_FAIL 模式：付款強制失敗，觸發 Saga 補償");
            return false;
        }

        // 正常模式：90% 成功率
        return Math.random() > 0.1;
    }
}