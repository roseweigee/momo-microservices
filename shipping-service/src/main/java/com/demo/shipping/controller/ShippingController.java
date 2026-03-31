package com.demo.shipping.controller;

import com.demo.shipping.model.ShipmentEntity;
import com.demo.shipping.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping API", description = "出貨管理")
public class ShippingController {

    private final ShippingService shippingService;

    @Operation(summary = "依訂單 ID 查詢出貨狀態")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ShipmentEntity> getByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(shippingService.getShipmentByOrderId(orderId));
    }

    @Operation(summary = "依出貨單 ID 查詢")
    @GetMapping("/{shipmentId}")
    public ResponseEntity<ShipmentEntity> getShipment(@PathVariable String shipmentId) {
        return ResponseEntity.ok(shippingService.getShipment(shipmentId));
    }

    @Operation(summary = "更新出貨狀態（admin only）")
    @PatchMapping("/{shipmentId}/status")
    public ResponseEntity<ShipmentEntity> updateStatus(
            @PathVariable String shipmentId,
            @RequestParam ShipmentEntity.ShipmentStatus status) {
        return ResponseEntity.ok(shippingService.updateStatus(shipmentId, status));
    }
}
