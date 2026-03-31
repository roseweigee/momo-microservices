package com.demo.inventory.service;

import com.demo.inventory.model.BatchRecord;
import com.demo.inventory.model.WarehouseEntity;
import com.demo.inventory.repository.BatchRecordRepository;
import com.demo.inventory.repository.WarehouseRepository;
import com.demo.inventory.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRouterService {

    private final WarehouseRepository warehouseRepo;
    private final WarehouseStockRepository stockRepo;
    private final BatchRecordRepository batchRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_PREFIX = "wh_stock:"; // wh_stock:{warehouseId}:{productId}
    private static final String MAIN_WAREHOUSE = "WH-MAIN-TAOYUAN";

    // Lua Script：原子扣減分倉 Redis 庫存（防超賣）
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil then return -1 end
            if stock < tonumber(ARGV[1]) then return -2 end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """, Long.class);

    /**
     * 分倉路由：找最近的有貨倉庫
     *
     * 步驟：
     * 1. 依客戶地址找最近的倉庫（Haversine 距離排序）
     * 2. 逐一檢查 Redis 庫存（O(1)，極快）
     * 3. 找到有貨且最近的倉 → 原子扣減 → 回傳
     * 4. 全部沒貨 → fallback 主倉
     */
    @Transactional
    public WarehouseAllocationResult allocateWarehouse(
            String productId, int quantity, double userLat, double userLng) {

        log.info("尋找最佳倉庫：productId={}, qty={}", productId, quantity);

        // 依距離排序所有倉庫
        List<WarehouseEntity> warehouses = warehouseRepo.findAllOrderByDistance(userLat, userLng);

        for (WarehouseEntity warehouse : warehouses) {
            String redisKey = STOCK_PREFIX + warehouse.getWarehouseId() + ":" + productId;

            // Redis 原子扣減
            Long remaining = redisTemplate.execute(
                    DEDUCT_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(quantity)
            );

            if (remaining == null || remaining < 0) {
                log.debug("倉庫 {} 庫存不足，嘗試下一個", warehouse.getWarehouseId());
                continue;
            }

            // Redis 扣成功 → MySQL 同步扣減
            int updated = stockRepo.deductStock(warehouse.getWarehouseId(), productId, quantity);
            if (updated == 0) {
                // MySQL 不一致，回滾 Redis
                redisTemplate.opsForValue().increment(redisKey, quantity);
                continue;
            }

            // FEFO：指派效期最短的批號給撿貨員
            BatchRecord assignedBatch = assignFEFOBatch(productId, warehouse.getWarehouseId(), quantity);

            log.info("分倉成功：warehouseId={}, batchId={}, location={}",
                    warehouse.getWarehouseId(),
                    assignedBatch != null ? assignedBatch.getBatchId() : "N/A",
                    assignedBatch != null ? assignedBatch.getLocation() : "N/A");

            return WarehouseAllocationResult.builder()
                    .warehouseId(warehouse.getWarehouseId())
                    .warehouseName(warehouse.getName())
                    .city(warehouse.getCity())
                    .batchId(assignedBatch != null ? assignedBatch.getBatchId() : null)
                    .pickingLocation(assignedBatch != null ? assignedBatch.getLocation() : null)
                    .remainingStock(remaining)
                    .isCrossRegion(!warehouse.getCity().equals(getUserCity(userLat, userLng)))
                    .build();
        }

        // 所有倉庫都沒貨
        log.warn("所有倉庫庫存不足：productId={}", productId);
        throw new IllegalStateException("庫存不足：productId=" + productId);
    }

    /**
     * FEFO 批號指派
     * 找效期最短的批號，告訴撿貨員去哪個貨架拿
     */
    @Transactional
    public BatchRecord assignFEFOBatch(String productId, String warehouseId, int quantity) {
        List<BatchRecord> batches = batchRepo.findAvailableBatchesFEFO(productId, warehouseId);

        for (BatchRecord batch : batches) {
            int updated = batchRepo.deductBatchStock(batch.getBatchId(), quantity);
            if (updated > 0) {
                log.info("FEFO 指派：batchId={}, expireDate={}, location={}",
                        batch.getBatchId(), batch.getExpireDate(), batch.getLocation());
                return batch;
            }
        }
        return null; // 無效期限制的商品
    }

    /**
     * 初始化倉庫 Redis 庫存（服務啟動或手動觸發）
     */
    public void syncWarehouseStockToRedis(String warehouseId, String productId) {
        stockRepo.findByWarehouseIdAndProductId(warehouseId, productId)
                .ifPresent(stock -> {
                    String key = STOCK_PREFIX + warehouseId + ":" + productId;
                    redisTemplate.opsForValue().set(key, stock.getQuantity());
                    log.info("Redis 庫存同步：{}={}", key, stock.getQuantity());
                });
    }

    /**
     * 查詢即將過期商品（預警用）
     */
    public List<BatchRecord> getExpiringBatches(int withinDays) {
        return batchRepo.findExpiringBatches(java.time.LocalDate.now().plusDays(withinDays));
    }

    // 簡化：依經緯度判斷城市（生產環境用 geocoding API）
    private String getUserCity(double lat, double lng) {
        if (lat > 24.9) return "台北";
        if (lat > 24.0) return "台中";
        return "高雄";
    }
}
