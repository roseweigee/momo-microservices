package com.demo.box.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDimension {
    private String productId;
    private int quantity;
    private double length;    // cm
    private double width;     // cm
    private double height;    // cm
    private double weightKg;  // kg
}
