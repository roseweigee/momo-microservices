package com.demo.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // ── 正常流程 Topics ──────────────────────────
    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name("order.confirmed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Saga 補償 Topics ─────────────────────────
    // shipping 失敗時，通知 order-service 取消訂單
    @Bean
    public NewTopic orderCancelTopic() {
        return TopicBuilder.name("order.cancel")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── DLQ Topics ───────────────────────────────
    // 處理失敗超過重試次數的訊息
    @Bean
    public NewTopic orderConfirmedDlqTopic() {
        return TopicBuilder.name("order.confirmed.DLQ")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelDlqTopic() {
        return TopicBuilder.name("order.cancel.DLQ")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
