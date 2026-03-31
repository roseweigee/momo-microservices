package com.demo.order.kafka;

import com.demo.order.model.OrderCancelEvent;
import com.demo.order.model.OrderEntity;
import com.demo.order.service.OrderService;
import com.demo.order.service.StockRedisService;
import com.demo.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaConsumer {

    private final OrderService orderService;
    private final StockRedisService stockRedisService;
    private final ProductRepository productRepository;

    /**
     * Saga 補償：接收 shipping-service 發來的取消事件
     * 取消訂單 + 還原庫存
     *
     * @RetryableTopic：自動重試 3 次，失敗後送進 DLQ
     */
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),  // 1s, 2s, 4s
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".DLQ"
    )
    @KafkaListener(topics = "order.cancel", groupId = "order-saga-group")
    @Transactional
    public void handleOrderCancel(@Payload OrderCancelEvent event) {
        log.warn("Saga 補償觸發：orderId={}, reason={}", event.getOrderId(), event.getReason());

        try {
            // 1. 更新訂單狀態為 CANCELLED
            orderService.updateStatus(event.getOrderId(), OrderEntity.OrderStatus.CANCELLED);
            log.info("訂單已取消：{}", event.getOrderId());

            // 2. 還原 MySQL 庫存
            productRepository.restoreStock(event.getProductId(), event.getQuantity());
            log.info("MySQL 庫存已還原：productId={}, qty={}", event.getProductId(), event.getQuantity());

            // 3. 還原 Redis 庫存（原子操作）
            stockRedisService.restoreStock(event.getProductId(), event.getQuantity());
            log.info("Redis 庫存已還原：productId={}, qty={}", event.getProductId(), event.getQuantity());

        } catch (Exception e) {
            log.error("Saga 補償失敗：orderId={}", event.getOrderId(), e);
            throw e; // 拋出讓 @RetryableTopic 觸發重試
        }
    }
}
