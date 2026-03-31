package com.demo.shipping.kafka;

import com.demo.shipping.model.OrderConfirmedEvent;
import com.demo.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingKafkaConsumer {

    private final ShippingService shippingService;
    private final ShippingCompensationProducer compensationProducer;

    /**
     * 主要 Consumer：處理訂單確認事件，建立出貨單
     *
     * @RetryableTopic 自動處理重試和 DLQ：
     * - 失敗後自動重試 3 次（1s, 2s, 4s 指數退避）
     * - 超過重試次數 → 自動送進 order.confirmed.DLQ
     * - 冪等保護：重複消費同一訂單不會建立兩個出貨單
     */
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".DLQ",
        include = {Exception.class}
    )
    @KafkaListener(
        topics = "order.confirmed",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleOrderConfirmed(
            @Payload OrderConfirmedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("收到訂單確認事件：orderId={}, partition={}, offset={}",
                event.getOrderId(), partition, offset);

        try {
            // 冪等保護：ShippingService 內部檢查 orderId 是否已存在
            shippingService.createShipment(event);
            log.info("出貨單建立成功：orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("出貨單建立失敗：orderId={}, error={}", event.getOrderId(), e.getMessage());
            // 拋出讓 @RetryableTopic 觸發重試
            // 重試耗盡後自動送進 DLQ，同時觸發 @DltHandler
            throw e;
        }
    }

    /**
     * DLQ Handler：訊息重試耗盡後執行
     *
     * 做兩件事：
     * 1. 記錄告警 log
     * 2. 觸發 Saga 補償：發 order.cancel 事件給 order-service
     *    → order-service 取消訂單、還原庫存
     */
    @DltHandler
    public void handleDlt(
            @Payload OrderConfirmedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.error("===== DLQ 訊息 =====");
        log.error("Topic: {}", topic);
        log.error("orderId: {}", event.getOrderId());
        log.error("重試耗盡，觸發 Saga 補償...");

        try {
            compensationProducer.sendCancelEvent(event, "出貨單建立失敗，重試耗盡");
            log.warn("Saga 補償事件已發送：orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Saga 補償失敗，需要人工處理：orderId={}", event.getOrderId(), e);
        }
    }
}
