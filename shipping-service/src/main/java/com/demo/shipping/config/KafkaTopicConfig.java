package com.demo.shipping.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCancelTopic() {
        return TopicBuilder.name("order.cancel")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderConfirmedDlqTopic() {
        return TopicBuilder.name("order.confirmed.DLQ")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
