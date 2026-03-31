package com.demo.box.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BoxCalculationRequest {
    @NotEmpty
    private List<ItemDimension> items;
}

@Data
class ItemDimensionDef {
    private String productId;
    @Min(1) private int quantity;
    private double length;   // cm
    private double width;    // cm
    private double height;   // cm
    private double weightKg; // kg
}
