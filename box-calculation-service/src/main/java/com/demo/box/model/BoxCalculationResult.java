package com.demo.box.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxCalculationResult {
    private boolean recommended;
    private boolean suggestSplit;
    private BoxSize selectedBox;
    private double totalVolumeUsedCm3;
    private double totalWeightKg;
    private double boxUtilizationRate;  // 空間使用率 %
    private String message;
}
