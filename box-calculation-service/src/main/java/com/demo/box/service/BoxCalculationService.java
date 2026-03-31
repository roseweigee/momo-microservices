package com.demo.box.service;

import com.demo.box.model.BoxCalculationRequest;
import com.demo.box.model.BoxCalculationResult;
import com.demo.box.model.BoxSize;
import com.demo.box.model.ItemDimension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 3D 裝箱計算服務
 *
 * 目的：
 * 找出最小能裝下所有商品的標準紙箱
 * 減少「大箱裝小貨」浪費的緩衝材和材積運費
 *
 * 演算法：
 * 1. 計算所有商品總體積
 * 2. 考慮 20% 緩衝空間（包材、填充物）
 * 3. 從小到大找第一個能裝下的標準箱
 * 4. 若沒有合適標準箱 → 建議客製紙箱
 *
 * 進階版可用「3D Bin Packing」演算法（NP-Hard 問題，生產環境用啟發式算法）
 */
@Slf4j
@Service
public class BoxCalculationService {

    // momo 標準紙箱規格（長×寬×高 cm，體積 cm³）
    private static final List<BoxSize> STANDARD_BOXES = List.of(
        new BoxSize("XS",  20, 15, 10,  0.5),   //  3,000 cm³
        new BoxSize("S",   30, 20, 15,  1.0),   //  9,000 cm³
        new BoxSize("M",   40, 30, 20,  2.0),   // 24,000 cm³
        new BoxSize("L",   50, 40, 30,  3.5),   // 60,000 cm³
        new BoxSize("XL",  60, 50, 40,  5.0),   //120,000 cm³
        new BoxSize("XXL", 80, 60, 50,  8.0)    //240,000 cm³
    );

    private static final double BUFFER_RATIO = 1.2;    // 20% 緩衝空間
    private static final double MAX_WEIGHT_KG = 30.0;  // 單箱最大重量

    public BoxCalculationResult calculate(BoxCalculationRequest request) {
        List<ItemDimension> items = request.getItems();

        // 計算總體積（含緩衝）
        double totalVolume = items.stream()
                .mapToDouble(i -> i.getLength() * i.getWidth() * i.getHeight() * i.getQuantity())
                .sum() * BUFFER_RATIO;

        // 計算總重量
        double totalWeight = items.stream()
                .mapToDouble(i -> i.getWeightKg() * i.getQuantity())
                .sum();

        // 最大單邊尺寸（確保最長的商品放得進去）
        double maxLength = items.stream()
                .mapToDouble(i -> Math.max(i.getLength(), Math.max(i.getWidth(), i.getHeight())))
                .max()
                .orElse(0);

        log.info("裝箱計算：totalVolume={}cm³, totalWeight={}kg, maxLength={}cm",
                String.format("%.0f", totalVolume),
                String.format("%.1f", totalWeight),
                String.format("%.0f", maxLength));

        // 找最小合適紙箱
        BoxSize selectedBox = STANDARD_BOXES.stream()
                .filter(box ->
                    box.volume() >= totalVolume &&              // 體積夠
                    box.maxDimension() >= maxLength &&          // 最長邊夠
                    box.getWeightLimitKg() >= totalWeight       // 承重夠
                )
                .min(Comparator.comparingDouble(BoxSize::volume))  // 選最小的
                .orElse(null);

        if (selectedBox == null) {
            // 商品太大或太重，需要客製紙箱或拆成多箱
            log.warn("無合適標準箱，建議拆包裝：totalVolume={}cm³", totalVolume);
            return BoxCalculationResult.builder()
                    .recommended(false)
                    .suggestSplit(true)
                    .message("商品體積超過最大標準箱，建議拆成 " +
                            (int) Math.ceil(totalVolume / STANDARD_BOXES.get(STANDARD_BOXES.size()-1).volume())
                            + " 個包裹")
                    .totalVolumeUsedCm3(totalVolume)
                    .totalWeightKg(totalWeight)
                    .build();
        }

        double utilizationRate = totalVolume / selectedBox.volume() * 100;

        log.info("選定紙箱：{}（{}×{}×{}cm），使用率：{:.1f}%",
                selectedBox.getSizeCode(),
                selectedBox.getLengthCm(), selectedBox.getWidthCm(), selectedBox.getHeightCm(),
                utilizationRate);

        return BoxCalculationResult.builder()
                .recommended(true)
                .suggestSplit(false)
                .selectedBox(selectedBox)
                .totalVolumeUsedCm3(totalVolume)
                .totalWeightKg(totalWeight)
                .boxUtilizationRate(utilizationRate)
                .message(String.format("建議使用 %s 號箱（%s×%s×%scm），空間使用率 %.1f%%",
                        selectedBox.getSizeCode(),
                        selectedBox.getLengthCm(), selectedBox.getWidthCm(), selectedBox.getHeightCm(),
                        utilizationRate))
                .build();
    }
}
