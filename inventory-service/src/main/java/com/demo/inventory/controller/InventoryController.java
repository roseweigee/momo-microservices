package com.demo.inventory.controller;

import com.demo.inventory.service.InventoryRouterService;
import com.demo.inventory.service.WarehouseAllocationResult;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory API", description = "分倉庫存 · FEFO · 分倉路由")
public class InventoryController {

    private final InventoryRouterService routerService;

    /**
     * 分倉路由 + FEFO 批號指派
     *
     * @RateLimiter 保護：
     * - 每秒最多允許 50 個請求
     * - 超過限制直接走 fallback，回傳 429 Too Many Requests
     * - 雙十一閃購時防止庫存路由計算被打爆
     *
     * 為什麼這裡要限流？
     * Haversine 計算 + Redis 查詢 + MySQL 扣減，每次都有 I/O
     * 無限制的話大量並發會讓 inventory-service 過載
     */
    @Operation(summary = "分倉路由 + FEFO 指派")
    @PostMapping("/allocate")
    @RateLimiter(name = "inventoryAllocate", fallbackMethod = "allocateFallback")
    public ResponseEntity<WarehouseAllocationResult> allocate(
            @RequestBody AllocateRequest request) {
        WarehouseAllocationResult result = routerService.allocateWarehouse(
                request.productId(),
                request.quantity(),
                request.userLat(),
                request.userLng()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Rate Limiter Fallback：請求被限流時回傳 429
     * 告知客戶端稍後重試，不是 500 系統錯誤
     */
    public ResponseEntity<Map<String, String>> allocateFallback(
            AllocateRequest request, RequestNotPermitted e) {
        log.warn("Rate Limiter 觸發！inventory/allocate 超過請求限制，productId={}",
                request.productId());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                    "error", "TOO_MANY_REQUESTS",
                    "message", "庫存路由請求超過限制，請稍後重試",
                    "retryAfter", "1s"
                ));
    }

    @Operation(summary = "查詢即將到期商品")
    @GetMapping("/expiring")
    public ResponseEntity<?> getExpiring(
            @RequestParam(defaultValue = "7") int withinDays) {
        return ResponseEntity.ok(routerService.getExpiringBatches(withinDays));
    }

    @Operation(summary = "同步倉庫庫存到 Redis")
    @PostMapping("/sync-redis")
    public ResponseEntity<Map<String, String>> syncRedis(
            @RequestParam String warehouseId,
            @RequestParam String productId) {
        routerService.syncWarehouseStockToRedis(warehouseId, productId);
        return ResponseEntity.ok(Map.of("status", "synced"));
    }

    record AllocateRequest(
        String productId,
        @Min(1) int quantity,
        double userLat,
        double userLng
    ) {}
}
