package com.demo.box.model;

import lombok.*;

@Data
@AllArgsConstructor
public class BoxSize {
    private String sizeCode;      // XS, S, M, L, XL, XXL
    private double lengthCm;
    private double widthCm;
    private double heightCm;
    private double weightLimitKg;

    public double volume() {
        return lengthCm * widthCm * heightCm;
    }

    public double maxDimension() {
        return Math.max(lengthCm, Math.max(widthCm, heightCm));
    }
}
