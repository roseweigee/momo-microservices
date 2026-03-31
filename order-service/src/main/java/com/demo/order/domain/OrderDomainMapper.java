package com.demo.order.domain;

import com.demo.order.model.OrderEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Domain ↔ JPA Entity 轉換
 *
 * 為什麼需要這個 Mapper？
 *
 * DDD 的 Domain 物件（Order）和 JPA Entity（OrderEntity）要分開：
 * - Domain 物件：純粹的業務邏輯，不依賴 JPA annotation
 * - JPA Entity：負責資料庫映射，不包含業務邏輯
 *
 * 分開的好處：
 * 1. Domain 物件可以獨立測試，不需要 Spring Context
 * 2. 資料庫 schema 變了不影響業務邏輯
 * 3. 業務邏輯變了不影響資料庫映射
 *
 * 實務上，小專案可以讓 JPA Entity 直接承載部分業務方法
 * 但要展示 DDD 概念，分開是最清晰的示範
 */
@Component
public class OrderDomainMapper {

    /**
     * Domain → JPA Entity（存資料庫前）
     */
    public OrderEntity toEntity(Order order) {
        return OrderEntity.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice().getAmount())
                .status(OrderEntity.OrderStatus.valueOf(order.getStatus().name()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * JPA Entity → Domain（從資料庫取出後）
     */
    public Order toDomain(OrderEntity entity) {
        return Order.create(
                entity.getUserId(),
                entity.getProductId(),
                entity.getQuantity(),
                Money.of(entity.getTotalPrice()
                        .divide(BigDecimal.valueOf(entity.getQuantity()),
                                2, java.math.RoundingMode.HALF_UP))
        );
    }
}
