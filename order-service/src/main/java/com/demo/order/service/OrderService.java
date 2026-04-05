package com.demo.order.service;

import com.demo.order.client.UserServiceClient;
import com.demo.order.kafka.OrderKafkaProducer;
import com.demo.order.model.*;
import com.demo.order.repository.OrderRepository;
import com.demo.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRedisService stockRedisService;
    private final OrderKafkaProducer kafkaProducer;
    private final UserServiceClient userServiceClient;

    @Transactional
    public OrderEntity createOrder(CreateOrderRequest request) {

        // 1. 跨服務驗證：確認 user 存在（呼叫 user-service）
        if (!userServiceClient.userExists(request.getUserId())) {
            throw new IllegalArgumentException("User not found: " + request.getUserId());
        }

        // 2. Redis Lua 原子扣減庫存（防超賣）
        long remaining = stockRedisService.deductStock(request.getProductId(), request.getQuantity());
        if (remaining == -1L) {
            ProductEntity product = productRepository.findByProductId(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));
            stockRedisService.initStock(request.getProductId(), product.getStock());
            remaining = stockRedisService.deductStock(request.getProductId(), request.getQuantity());
        }
        if (remaining == -2L) {
            throw new IllegalStateException("Insufficient stock for: " + request.getProductId());
        }

        // 3. MySQL 扣減（最終一致性）
        int updated = productRepository.deductStock(request.getProductId(), request.getQuantity());
        if (updated == 0) {
            stockRedisService.initStock(request.getProductId(), 0);
            throw new IllegalStateException("Stock deduction failed at DB level");
        }

        // 4. 取得商品價格
        ProductEntity product = productRepository.findByProductId(request.getProductId()).orElseThrow();

        // 5. 建立訂單
        OrderEntity order = OrderEntity.builder()
                .orderId(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalPrice(product.getPrice().multiply(java.math.BigDecimal.valueOf(request.getQuantity())))
                .status(OrderEntity.OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);
        log.info("Order saved: {}", order.getOrderId());

        // 6. 發 Kafka 事件給 shipping-service
        kafkaProducer.sendOrderConfirmedEvent(OrderConfirmedEvent.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice())
                .confirmedAt(LocalDateTime.now())
                .build());

        return order;
    }

    public OrderEntity getOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    @Transactional
    public OrderEntity updateStatus(String orderId, OrderEntity.OrderStatus status) {
        OrderEntity order = getOrder(orderId);
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }
}
