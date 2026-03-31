package com.demo.order.kafka;

import com.demo.order.model.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {

    @Value("${app.kafka.topic.order-confirmed}")
    private String orderConfirmedTopic;

    private final KafkaTemplate<String, OrderConfirmedEvent> kafkaTemplate;

    public void sendOrderConfirmedEvent(OrderConfirmedEvent event) {
        kafkaTemplate.send(orderConfirmedTopic, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("OrderConfirmedEvent sent: orderId={}, partition={}, offset={}",
                                event.getOrderId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send event: orderId={}", event.getOrderId(), ex);
                    }
                });
    }
}
