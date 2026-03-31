package com.demo.order.repository;

import com.demo.order.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderId(String orderId);

    /**
     * 查詢可以併單的訂單
     * Hibernate 6 不支援 IN (ClassName.ENUM) 語法
     * 改用 :statuses 參數傳入 enum 集合
     */
    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.userId = :userId
          AND o.status IN :statuses
          AND o.createdAt >= :windowStart
          AND o.orderId <> :excludeOrderId
        ORDER BY o.createdAt DESC
        """)
    List<OrderEntity> findMergeableOrders(
        @Param("userId") String userId,
        @Param("statuses") List<OrderEntity.OrderStatus> statuses,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("excludeOrderId") String excludeOrderId
    );
}
