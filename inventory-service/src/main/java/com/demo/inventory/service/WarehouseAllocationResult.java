package com.demo.inventory.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarehouseAllocationResult {
    private String warehouseId;
    private String warehouseName;
    private String city;
    private String batchId;           // FEFO 指派的批號
    private String pickingLocation;   // 撿貨位置（貨架號）
    private Long remainingStock;      // 剩餘庫存
    private boolean isCrossRegion;    // 是否跨區配送（會影響運費）
}
