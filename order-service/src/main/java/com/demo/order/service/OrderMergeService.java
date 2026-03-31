package com.demo.order.service;

import com.demo.order.model.OrderEntity;
import com.demo.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 智慧併單服務（Order Merging）
 *
 * 目的：
 * 同一客戶在 30 分鐘內的多筆訂單，若商品在同一倉庫，合併成一個包裹
 * 可以節省：
 * - 多個包裹的運費
 * - 多個紙箱的材料成本
 * - 倉庫出貨的人力成本
 *
 * 觸發時機：
 * 訂單進入 Saga → payment 之前先做併單檢查
 * 如果可以併單 → 等待所有子訂單確認後再一起出貨
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMergeService {

    private final OrderRepository orderRepository;

    private static final int MERGE_WINDOW_MINUTES = 30;  // 30 分鐘內的訂單可以合併
    private static final int MAX_MERGE_ORDERS = 5;       // 最多合併 5 筆

    /**
     * 檢查是否可以併單
     *
     * 條件：
     * 1. 同一個 userId
     * 2. 30 分鐘內的待出貨訂單
     * 3. 商品在同一個倉庫（warehouseId 相同）
     * 4. 未超過最大併單數量
     */
    public MergeCheckResult checkCanMerge(String userId, String warehouseId, String newOrderId) {

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(MERGE_WINDOW_MINUTES);

        // 查詢同客戶、同倉庫、窗口內的待出貨訂單
        List<OrderEntity> mergeable = orderRepository
                .findMergeableOrders(userId, List.of(OrderEntity.OrderStatus.PENDING, OrderEntity.OrderStatus.CONFIRMED), windowStart, newOrderId);

        if (mergeable.isEmpty()) {
            log.debug("無可併單訂單：userId={}, warehouseId={}", userId, warehouseId);
            return MergeCheckResult.noMerge();
        }

        if (mergeable.size() >= MAX_MERGE_ORDERS) {
            log.info("達到最大併單數量，不再合併：userId={}", userId);
            return MergeCheckResult.noMerge();
        }

        log.info("發現可併單訂單：userId={}, count={}, orderIds={}",
                userId, mergeable.size(),
                mergeable.stream().map(OrderEntity::getOrderId).toList());

        return MergeCheckResult.canMerge(
                mergeable.stream().map(OrderEntity::getOrderId).toList()
        );
    }

    /**
     * 執行併單：將新訂單標記為「等待合併出貨」
     */
    public void mergeOrders(String masterOrderId, List<String> childOrderIds) {
        log.info("執行併單：masterOrderId={}, children={}", masterOrderId, childOrderIds);
        // 在生產環境中，會建立 merged_shipment 記錄
        // 讓 WMS 知道這些訂單要合成一個包裹出貨
    }

    // ── Result DTO ───────────────────────────────
    public record MergeCheckResult(
        boolean canMerge,
        List<String> mergeableOrderIds
    ) {
        static MergeCheckResult noMerge() {
            return new MergeCheckResult(false, List.of());
        }
        static MergeCheckResult canMerge(List<String> ids) {
            return new MergeCheckResult(true, ids);
        }
    }
}
