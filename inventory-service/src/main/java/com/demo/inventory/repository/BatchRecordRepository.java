package com.demo.inventory.repository;

import com.demo.inventory.model.BatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BatchRecordRepository extends JpaRepository<BatchRecord, Long> {

    @Query("""
        SELECT b FROM BatchRecord b
        WHERE b.productId = :productId
          AND b.warehouseId = :warehouseId
          AND b.quantity > 0
        ORDER BY
          CASE WHEN b.expireDate IS NULL THEN 1 ELSE 0 END,
          b.expireDate ASC
        """)
    List<BatchRecord> findAvailableBatchesFEFO(
        @Param("productId") String productId,
        @Param("warehouseId") String warehouseId
    );

    @Modifying
    @Query("""
        UPDATE BatchRecord b
        SET b.quantity = b.quantity - :qty,
            b.updatedAt = CURRENT_TIMESTAMP
        WHERE b.batchId = :batchId
          AND b.quantity >= :qty
        """)
    int deductBatchStock(@Param("batchId") String batchId, @Param("qty") int qty);

    /**
     * 查詢即將到期的批號（效期 < N 天後的日期）
     * 改用 :expiryThreshold 傳入計算好的日期，避免 Hibernate 6 日期加法問題
     */
    @Query("""
        SELECT b FROM BatchRecord b
        WHERE b.expireDate IS NOT NULL
          AND b.expireDate < :expiryThreshold
          AND b.quantity > 0
        ORDER BY b.expireDate ASC
        """)
    List<BatchRecord> findExpiringBatches(@Param("expiryThreshold") LocalDate expiryThreshold);
}
