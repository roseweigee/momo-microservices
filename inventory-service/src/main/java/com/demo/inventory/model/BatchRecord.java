package com.demo.inventory.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批號庫存記錄（FEFO 核心）
 * 每一批入庫的商品都有獨立記錄
 * 包含效期、倉庫位置、數量
 */
@Entity
@Table(name = "batch_records",
       indexes = {
           @Index(name = "idx_product_warehouse", columnList = "product_id, warehouse_id"),
           @Index(name = "idx_expire_date", columnList = "expire_date")
       })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, unique = true)
    private String batchId;         // 批號，例如：BATCH-2024-03-001

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "location", nullable = false)
    private String location;        // 倉庫貨架位置，例如：A-3-07

    @Column(name = "expire_date")
    private LocalDate expireDate;   // 效期（null 代表無效期限制）

    @Column(nullable = false)
    private Integer quantity;       // 剩餘數量

    @Column(name = "received_at")
    private LocalDateTime receivedAt;  // 入庫時間

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
