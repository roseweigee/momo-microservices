package com.demo.order.controller;

import com.demo.order.model.CreateOrderRequest;
import com.demo.order.model.OrderEntity;
import com.demo.order.service.OrderService;
import com.demo.order.service.StockRedisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "訂單管理")
public class OrderController {

    private final OrderService orderService;
    private final StockRedisService stockRedisService;

    @Operation(summary = "建立訂單", description = "驗證 user → Redis 扣庫存 → MySQL 落地 → Kafka 通知 shipping-service")
    @PostMapping
    public ResponseEntity<OrderEntity> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @Operation(summary = "查詢訂單")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderEntity> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @Operation(summary = "更新訂單狀態（admin only）")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderEntity> updateStatus(
            @PathVariable String orderId,
            @RequestParam OrderEntity.OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, status));
    }

    @Operation(summary = "查詢 Redis 庫存")
    @GetMapping("/stock/{productId}")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String productId) {
        Long stock = stockRedisService.getStock(productId);
        return ResponseEntity.ok(Map.of("productId", productId, "redisStock", stock != null ? stock : "未初始化"));
    }
}
