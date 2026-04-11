package com.demo.order.saga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorIdempotencyTest {

    @Mock SagaStateRepository sagaStateRepository;
    @Mock com.demo.order.repository.OrderRepository orderRepository;
    @Mock com.demo.order.repository.ProductRepository productRepository;
    @Mock com.demo.order.service.StockRedisService stockRedisService;
    @Mock org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks SagaOrchestrator orchestrator;

    @Test
    void compensate_已補償_應跳過不重複執行() {
        // GIVEN：saga_state 已經是 COMPENSATED
        SagaState state = SagaState.builder()
                .orderId("test-order-001")
                .status(SagaState.SagaStatus.COMPENSATED)
                .productId("prod-001")
                .quantity(2)
                .build();

        // WHEN：重複觸發補償
        orchestrator.compensate(state, "重複觸發測試");

        // THEN：不應該執行任何還原動作
        verify(orderRepository, never()).findByOrderId(any());
        verify(productRepository, never()).restoreStock(any(), anyInt());
        verify(stockRedisService, never()).restoreStock(any(), anyInt());
    }

    @Test
    void compensate_補償中_應跳過不重複執行() {
        SagaState state = SagaState.builder()
                .orderId("test-order-002")
                .status(SagaState.SagaStatus.COMPENSATING)
                .productId("prod-001")
                .quantity(2)
                .build();

        orchestrator.compensate(state, "重複觸發測試");

        verify(orderRepository, never()).findByOrderId(any());
        verify(productRepository, never()).restoreStock(any(), anyInt());
        verify(stockRedisService, never()).restoreStock(any(), anyInt());
    }
}