package com.demo.inventory.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "warehouses")
@Data @NoArgsConstructor @AllArgsConstructor
public class WarehouseEntity {

    @Id
    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(nullable = false)
    private String name;

    private String city;        // 台北、新北、台中、高雄
    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private WarehouseType type;

    public enum WarehouseType {
        MAIN,       // 主倉（大型）
        SATELLITE   // 衛星倉（小型，靠近客戶）
    }
}
