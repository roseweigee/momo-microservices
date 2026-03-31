package com.demo.shipping.service;

import com.demo.shipping.model.OrderConfirmedEvent;
import com.demo.shipping.model.ShipmentEntity;
import com.demo.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShipmentRepository shipmentRepository;

    @Transactional
    public ShipmentEntity createShipment(OrderConfirmedEvent event) {
        // 冪等設計：同一個 orderId 不重複建立出貨單
        return shipmentRepository.findByOrderId(event.getOrderId())
                .orElseGet(() -> {
                    String trackingNo = "TW" + System.currentTimeMillis();
                    ShipmentEntity shipment = ShipmentEntity.builder()
                            .shipmentId(UUID.randomUUID().toString())
                            .orderId(event.getOrderId())
                            .userId(event.getUserId())
                            .status(ShipmentEntity.ShipmentStatus.PREPARING)
                            .trackingNo(trackingNo)
                            .address("待確認")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    shipmentRepository.save(shipment);
                    log.info("Shipment created: {} for orderId: {}", shipment.getShipmentId(), event.getOrderId());
                    return shipment;
                });
    }

    public ShipmentEntity getShipmentByOrderId(String orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for orderId: " + orderId));
    }

    public ShipmentEntity getShipment(String shipmentId) {
        return shipmentRepository.findByShipmentId(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
    }

    @Transactional
    public ShipmentEntity updateStatus(String shipmentId, ShipmentEntity.ShipmentStatus status) {
        ShipmentEntity shipment = getShipment(shipmentId);
        shipment.setStatus(status);
        shipment.setUpdatedAt(LocalDateTime.now());
        return shipmentRepository.save(shipment);
    }
}
