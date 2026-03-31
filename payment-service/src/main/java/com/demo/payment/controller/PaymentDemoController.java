package com.demo.payment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo 專用 Controller
 * 提供切換「強制付款失敗」的 API
 * 讓面試 Demo 可以隨時觸發 Saga 補償流程
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/demo")
public class PaymentDemoController {

    // 使用 AtomicBoolean 保證執行緒安全
    public static final AtomicBoolean FORCE_FAIL = new AtomicBoolean(false);

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
            "forceFail", FORCE_FAIL.get(),
            "message", FORCE_FAIL.get()
                ? "⚠️ 強制失敗模式：所有付款都會失敗，觸發 Saga 補償"
                : "✅ 正常模式：付款 90% 成功率"
        );
    }

    @PostMapping("/force-fail/{enabled}")
    public Map<String, Object> setForceFail(@PathVariable boolean enabled) {
        FORCE_FAIL.set(enabled);
        log.warn("Demo 模式切換：forceFail={}", enabled);
        return Map.of(
            "forceFail", FORCE_FAIL.get(),
            "message", enabled
                ? "⚠️ 已開啟強制失敗 → 下一筆訂單付款會失敗，觸發 Saga 補償"
                : "✅ 已關閉強制失敗 → 恢復正常付款流程"
        );
    }
}
