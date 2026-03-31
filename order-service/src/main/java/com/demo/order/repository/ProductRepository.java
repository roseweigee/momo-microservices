package com.demo.order.repository;

import com.demo.order.model.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByProductId(String productId);

    @Modifying
    @Query("UPDATE ProductEntity p SET p.stock = p.stock - :qty WHERE p.productId = :productId AND p.stock >= :qty")
    int deductStock(String productId, int qty);

    // 還原庫存（Saga 補償用）
    @Modifying
    @Query("UPDATE ProductEntity p SET p.stock = p.stock + :qty WHERE p.productId = :productId")
    int restoreStock(String productId, int qty);
}
