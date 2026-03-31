package com.demo.shipping.repository;

import com.demo.shipping.model.ShipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<ShipmentEntity, Long> {
    Optional<ShipmentEntity> findByShipmentId(String shipmentId);
    Optional<ShipmentEntity> findByOrderId(String orderId);
}
