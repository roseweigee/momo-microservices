package com.demo.inventory.repository;

import com.demo.inventory.model.WarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    Optional<WarehouseStock> findByWarehouseIdAndProductId(String warehouseId, String productId);

    @Modifying
    @Query("UPDATE WarehouseStock s SET s.quantity = s.quantity - :qty WHERE s.warehouseId = :warehouseId AND s.productId = :productId AND s.quantity >= :qty")
    int deductStock(String warehouseId, String productId, int qty);

    @Modifying
    @Query("UPDATE WarehouseStock s SET s.quantity = s.quantity + :qty WHERE s.warehouseId = :warehouseId AND s.productId = :productId")
    int addStock(String warehouseId, String productId, int qty);
}
