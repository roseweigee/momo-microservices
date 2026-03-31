package com.demo.order.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 跨服務呼叫：order-service → user-service
 *
 * 三層防護：
 *
 * 1. @Bulkhead（艙壁模式）
 *    隔離呼叫 user-service 的執行緒池
 *    最多 10 個 Thread 同時呼叫 user-service
 *    超過排隊上限直接 fallback，不影響 order-service 其他功能
 *
 *    為什麼需要 Bulkhead？
 *    假設 user-service 變慢，沒有 Bulkhead 的話：
 *    → 大量 Thread 卡在等待 user-service 回應
 *    → order-service 的 Thread Pool 被耗盡
 *    → 連訂單查詢、庫存扣減都無法執行
 *    → 整個 order-service 雪崩
 *
 *    有 Bulkhead：
 *    → user-service 變慢，最多只佔 10 個 Thread
 *    → 其他業務功能不受影響
 *    → 隔離故障範圍，就像船艙的隔板
 *
 * 2. @CircuitBreaker（熔斷器）
 *    失敗率超過 50% 熔斷，直接 fallback 不等待
 *
 * 3. @Retry（重試）
 *    短暫網路問題自動重試 3 次，指數退避
 *
 * 執行順序：Bulkhead → CircuitBreaker → Retry → 實際呼叫
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    @Value("${app.services.user-service-url}")
    private String userServiceUrl;

    private final RestTemplate restTemplate;

    @Bulkhead(name = "userService", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "userExistsFallback")
    @CircuitBreaker(name = "userService", fallbackMethod = "userExistsFallback")
    @Retry(name = "userService")
    public boolean userExists(String userId) {
        String url = userServiceUrl + "/api/users/internal/" + userId;
        log.info("呼叫 user-service 驗證用戶：{}", userId);
        restTemplate.getForObject(url, Object.class);
        log.info("用戶驗證成功：{}", userId);
        return true;
    }

    /**
     * Fallback：Bulkhead 滿載、Circuit 熔斷、Retry 耗盡 任一觸發時執行
     *
     * Fail-Open 策略：暫時允許通過
     * 理由：用戶驗證是低風險操作，寧可短暫允許少數假用戶，
     *       也不要因 user-service 故障讓所有訂單失敗
     *
     * 對比 Fail-Fast（payment-service）：
     * 付款是高風險操作，超時直接拒絕，絕不允許含糊
     */
    public boolean userExistsFallback(String userId, Exception e) {
        log.warn("Resilience4j 觸發 fallback！userId={}, reason={}, type={}",
                userId, e.getMessage(), e.getClass().getSimpleName());
        return true; // Fail-Open
    }
}
