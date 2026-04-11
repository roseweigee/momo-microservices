package com.demo.order.saga;

import com.demo.order.model.OrderEntity;
import com.demo.order.repository.OrderRepository;
import com.demo.order.repository.ProductRepository;
import com.demo.order.service.StockRedisService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Saga Orchestrator（編排式 Saga）
 *
 * 完整下單流程（Orchestration）：
 *
 * Step 1: order-service 建立訂單（PENDING）
 *                │
 *                ▼
 * Step 2: Orchestrator → payment.process → payment-service 付款
 *                │
 *         付款成功？
 *         ├── 否 → 補償：取消訂單 + 還原庫存
 *         └── 是
 *                │
 *                ▼
 * Step 3: Orchestrator → order.confirmed → shipping-service 建出貨單
 *                │
 *                ▼
 * Step 4: 全部完成，訂單 CONFIRMED ✅
 *
 * 補償順序（逆序）：
 * ⑤ 取消出貨單 → ③ 取消訂單 → ② 還原 MySQL 庫存 → ① 還原 Redis 庫存 → 退款
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final OrderRepository orderRepository;
    private final StockRedisService stockRedisService;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Step 1: 啟動 Saga ────────────────────────
    @Transactional
    public void startSaga(String orderId, String userId, String productId,
                          Integer quantity, BigDecimal totalPrice) {
        log.info("Saga 啟動：orderId={}", orderId);

        // 記錄 Saga 狀態
        SagaState state = SagaState.builder()
                .orderId(orderId)
                .status(SagaState.SagaStatus.STARTED)
                .currentStep("PAYMENT")
                .productId(productId)
                .quantity(quantity)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        sagaStateRepository.save(state);

        // Step 2: 發送付款指令給 payment-service
        ProcessPaymentCommand cmd = new ProcessPaymentCommand(
                orderId, userId, totalPrice, LocalDateTime.now());
        kafkaTemplate.send("payment.process", orderId, cmd);

        state.setStatus(SagaState.SagaStatus.PAYMENT_REQUESTED);
        sagaStateRepository.save(state);
        log.info("付款指令已發送：orderId={}", orderId);
    }

    // ── Step 2: 接收付款結果 ─────────────────────
    @KafkaListener(topics = "payment.result", groupId = "saga-orchestrator-group")
    @Transactional
    public void handlePaymentResult(@Payload PaymentResultEvent event) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        event = mapper.readValue(payload, PaymentResultEvent.class);
        log.info("收到付款結果：orderId={}, success={}", event.getOrderId(), event.isSuccess());
        SagaState state = sagaStateRepository.findById(event.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Saga state not found: " + event.getOrderId()));

        if (event.isSuccess()) {
            // 付款成功 → Step 3: 通知 shipping-service
            state.setStatus(SagaState.SagaStatus.PAYMENT_SUCCESS);
            state.setPaymentId(event.getPaymentId());
            state.setCurrentStep("SHIPPING");
            sagaStateRepository.save(state);

            // 發訂單確認事件給 shipping-service
            OrderConfirmedForShipping shipCmd = new OrderConfirmedForShipping(
                    event.getOrderId(), state.getProductId(), state.getQuantity());
            kafkaTemplate.send("order.confirmed", event.getOrderId(), shipCmd);

            state.setStatus(SagaState.SagaStatus.SHIPPING_REQUESTED);
            sagaStateRepository.save(state);
            log.info("出貨指令已發送：orderId={}", event.getOrderId());

        } else {
            // 付款失敗 → 開始補償
            log.warn("付款失敗，開始 Saga 補償：orderId={}, reason={}",
                    event.getOrderId(), event.getFailureReason());
            state.setStatus(SagaState.SagaStatus.PAYMENT_FAILED);
            state.setFailureReason(event.getFailureReason());
            sagaStateRepository.save(state);

            compensate(state, "付款失敗：" + event.getFailureReason());
        }
    }

    // ── 補償流程（逆序）─────────────────────────
    @Transactional
    public void compensate(SagaState state, String reason) {
        log.warn("開始補償流程：orderId={}, reason={}", state.getOrderId(), reason);
        state.setStatus(SagaState.SagaStatus.COMPENSATING);
        sagaStateRepository.save(state);

        try {
            // ③ 取消訂單
            orderRepository.findByOrderId(state.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderEntity.OrderStatus.CANCELLED);
                order.setUpdatedAt(java.time.LocalDateTime.now());
                orderRepository.save(order);
            });
            log.info("訂單已取消：{}", state.getOrderId());

            // ② 還原 MySQL 庫存
            productRepository.restoreStock(state.getProductId(), state.getQuantity());
            log.info("MySQL 庫存已還原：productId={}", state.getProductId());

            // ① 還原 Redis 庫存
            stockRedisService.restoreStock(state.getProductId(), state.getQuantity());
            log.info("Redis 庫存已還原：productId={}", state.getProductId());

            // 如果已付款，發退款指令
            if (state.getPaymentId() != null) {
                RefundPaymentCommand refund = new RefundPaymentCommand(
                        state.getOrderId(), state.getPaymentId(), reason, LocalDateTime.now());
                kafkaTemplate.send("payment.refund", state.getOrderId(), refund);
                log.info("退款指令已發送：orderId={}", state.getOrderId());
            }

            state.setStatus(SagaState.SagaStatus.COMPENSATED);
            sagaStateRepository.save(state);
            log.info("Saga 補償完成：orderId={}", state.getOrderId());

        } catch (Exception e) {
            // 補償失敗 → 需要人工介入
            state.setStatus(SagaState.SagaStatus.FAILED);
            state.setFailureReason("補償失敗，需人工介入：" + e.getMessage());
            sagaStateRepository.save(state);
            log.error("Saga 補償失敗，需人工處理：orderId={}", state.getOrderId(), e);
        }
    }
}

// ── 內部 Command/Event 類別 ──────────────────────

@Data
class ProcessPaymentCommand {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private LocalDateTime requestedAt;

    ProcessPaymentCommand(String orderId, String userId, BigDecimal amount, LocalDateTime requestedAt) {
        this.orderId = orderId; this.userId = userId;
        this.amount = amount; this.requestedAt = requestedAt;
    }
}

@Data
class PaymentResultEvent {
    private String orderId;
    private String paymentId;
    private boolean success;
    private String failureReason;
    private LocalDateTime processedAt;
}

@Data
class OrderConfirmedForShipping {
    private String orderId;
    private String productId;
    private Integer quantity;

    OrderConfirmedForShipping(String orderId, String productId, Integer quantity) {
        this.orderId = orderId; this.productId = productId; this.quantity = quantity;
    }
}

@Data
class RefundPaymentCommand {
    private String orderId;
    private String paymentId;
    private String reason;
    private LocalDateTime requestedAt;

    RefundPaymentCommand(String orderId, String paymentId, String reason, LocalDateTime requestedAt) {
        this.orderId = orderId; this.paymentId = paymentId;
        this.reason = reason; this.requestedAt = requestedAt;
    }
}
