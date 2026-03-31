package com.demo.payment.service;

import com.demo.payment.model.PaymentEntity;
import com.demo.payment.model.PaymentResultEvent;
import com.demo.payment.repository.PaymentRepository;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentResultEvent> kafkaTemplate;

    private static final String PAYMENT_RESULT_TOPIC = "payment.result";

    @Value("${app.payment.force-fail:false}")
    private boolean forceFail;

    /**
     * 付款處理
     *
     * @TimeLimiter 保護：
     * - 超過 5 秒未完成 → 直接 fallback，回傳付款失敗
     * - 觸發 Saga 補償，訂單取消
     *
     * 為什麼付款需要 TimeLimiter？
     * 真實金流 API（綠界、藍新）有時會很慢
     * 如果不設 timeout，Saga Orchestrator 會一直等待
     * 導致訂單卡在 PAYMENT_REQUESTED 狀態
     * 用戶體驗極差：「我付款了，訂單為什麼不動？」
     *
     * Fail-Fast 策略（對比 user-service 的 Fail-Open）：
     * 付款是高風險操作，寧可明確拒絕觸發退款流程，
     * 也不能讓金額處於不確定狀態
     */
    @TimeLimiter(name = "paymentProcess", fallbackMethod = "processPaymentTimeout")
    @Transactional
    public CompletableFuture<Void> processPaymentAsync(
            String orderId, String userId, BigDecimal amount) {
        return CompletableFuture.runAsync(() -> {
            processPayment(orderId, userId, amount);
        });
    }

    /**
     * TimeLimiter Fallback：付款超時
     * Fail-Fast：超時視同付款失敗，觸發 Saga 補償
     */
    public CompletableFuture<Void> processPaymentTimeout(
            String orderId, String userId, BigDecimal amount, TimeoutException e) {
        log.error("付款超時！orderId={}, 超過 5 秒未完成，觸發 Saga 補償", orderId);

        // 發送付款失敗事件給 SagaOrchestrator
        kafkaTemplate.send(PAYMENT_RESULT_TOPIC, orderId,
                PaymentResultEvent.builder()
                        .orderId(orderId)
                        .paymentId(null)
                        .success(false)
                        .failureReason("付款超時（超過 5 秒）")
                        .processedAt(LocalDateTime.now())
                        .build());

        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public void processPayment(String orderId, String userId, BigDecimal amount) {
        // 冪等保護
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
                .status(success
                        ? PaymentEntity.PaymentStatus.SUCCESS
                        : PaymentEntity.PaymentStatus.FAILED)
                .failureReason(success ? null : "模擬付款失敗：餘額不足")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        log.info("Payment {}: orderId={}, amount={}",
                success ? "SUCCESS" : "FAILED", orderId, amount);

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
     * 模擬付款：90% 成功率
     * forceFail=true 時 100% 失敗（Demo Saga 補償用）
     */
    private boolean simulatePayment(BigDecimal amount) {
        if (forceFail) {
            log.warn("強制付款失敗模式（Demo）");
            return false;
        }
        return Math.random() > 0.1;
    }
}
