package com.demo.inventory.scheduler;

import com.demo.inventory.model.BatchRecord;
import com.demo.inventory.service.InventoryRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 預測性撥貨排程
 *
 * 功能：
 * 1. 每天凌晨掃描即將到期商品 → 優先銷售
 * 2. 每小時檢查衛星倉庫存水位 → 不足時從主倉補貨
 * 3. （進階）對接 AI 預測模型，提前把熱銷品放到衛星倉
 *
 * 這樣可以：
 * - 減少跨區配送（客戶附近的倉有貨）
 * - 減少效期損耗（快到期的優先賣）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishmentScheduler {

    private final InventoryRouterService inventoryRouterService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 每天凌晨 2 點：掃描 7 天內到期商品
     * 發 Kafka 事件給 WMS，請倉庫人員將這些商品移到容易撿到的位置
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scanExpiringInventory() {
        log.info("開始掃描即將到期商品（7天內）...");

        List<BatchRecord> expiring = inventoryRouterService.getExpiringBatches(7);

        if (expiring.isEmpty()) {
            log.info("無即將到期商品");
            return;
        }

        log.warn("發現 {} 批次即將到期商品，發送優先銷售指令", expiring.size());

        expiring.forEach(batch -> {
            // 發 Kafka 事件給 WMS，請倉庫調整這批貨的擺放位置
            kafkaTemplate.send("inventory.expiry-alert", batch.getBatchId(),
                    Map.of(
                        "batchId", batch.getBatchId(),
                        "productId", batch.getProductId(),
                        "warehouseId", batch.getWarehouseId(),
                        "location", batch.getLocation(),
                        "expireDate", batch.getExpireDate().toString(),
                        "quantity", batch.getQuantity(),
                        "action", "PRIORITIZE_PICKING"
                    ));

            log.info("效期預警：batchId={}, expireDate={}, qty={}",
                    batch.getBatchId(), batch.getExpireDate(), batch.getQuantity());
        });
    }

    /**
     * 每小時：檢查衛星倉庫存水位
     * 若某商品低於安全庫存，從主倉補貨到衛星倉
     *
     * 這就是「預測性撥貨」的核心：
     * 不等客戶下單才發現沒貨，提前補到最近的倉
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkSatelliteStockLevel() {
        log.info("檢查衛星倉庫存水位...");

        // 生產環境：查詢所有衛星倉的庫存
        // 比對安全庫存閾值（來自 AI 預測或歷史銷售資料）
        // 發補貨指令

        // 簡化示意：
        Map<String, Integer> safetyStock = Map.of(
            "prod-001", 20,  // iPhone：衛星倉至少要有 20 個
            "prod-002", 50,  // AirPods：至少 50 個
            "prod-003", 10   // MacBook：至少 10 個
        );

        log.info("安全庫存檢查完成，已觸發補貨流程");
        // 實際補貨邏輯：從主倉 transfer 到衛星倉
    }
}
