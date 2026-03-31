package com.demo.shipping.kafka;

import com.demo.shipping.model.OrderCancelEvent;
import com.demo.shipping.model.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Saga 補償 Producer
 * 出貨失敗時，發送取消事件給 order-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingCompensationProducer {

    private static final String ORDER_CANCEL_TOPIC = "order.cancel";

    private final KafkaTemplate<String, OrderCancelEvent> kafkaTemplate;

    public void sendCancelEvent(OrderConfirmedEvent event, String reason) {
        OrderCancelEvent cancelEvent = OrderCancelEvent.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .reason(reason)
                .cancelledAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ORDER_CANCEL_TOPIC, event.getOrderId(), cancelEvent)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Saga 補償事件發送成功：orderId={}", event.getOrderId());
                    } else {
                        log.error("Saga 補償事件發送失敗：orderId={}", event.getOrderId(), ex);
                    }
                });
    }
}
