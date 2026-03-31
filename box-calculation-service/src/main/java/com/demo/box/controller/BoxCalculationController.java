package com.demo.box.controller;

import com.demo.box.model.BoxCalculationRequest;
import com.demo.box.model.BoxCalculationResult;
import com.demo.box.service.BoxCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/box")
@RequiredArgsConstructor
@Tag(name = "Box Calculation API", description = "3D 裝箱計算 · 最佳紙箱選擇 · 材積運費優化")
public class BoxCalculationController {

    private final BoxCalculationService boxCalculationService;

    @Operation(
        summary = "計算最佳裝箱方案",
        description = """
            輸入商品尺寸和數量，回傳最適合的標準紙箱。
            
            **算法邏輯：**
            1. 計算所有商品總體積（含 20% 緩衝）
            2. 考慮最長邊限制
            3. 從小到大找第一個能裝下的標準箱
            4. 超出最大箱 → 建議拆包裝
            
            **標準紙箱規格：**
            - XS: 20×15×10 cm
            - S:  30×20×15 cm
            - M:  40×30×20 cm
            - L:  50×40×30 cm
            - XL: 60×50×40 cm
            - XXL: 80×60×50 cm
            """
    )
    @PostMapping("/calculate")
    public ResponseEntity<BoxCalculationResult> calculate(
            @Valid @RequestBody BoxCalculationRequest request) {
        return ResponseEntity.ok(boxCalculationService.calculate(request));
    }
}
