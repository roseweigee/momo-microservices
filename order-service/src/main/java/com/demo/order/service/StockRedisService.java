package com.demo.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis 庫存服務
 *
 * 核心：Lua Script 原子扣減，防止超賣
 * Lua Script 在 Redis 單線程執行，不可被中斷
 * 100 萬並發也不會超賣
 *
 * 返回值語意：
 *  >= 0：扣減成功，返回剩餘數量
 *  -1：商品不存在
 *  -2：庫存不足
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_PREFIX = "stock:";

    // Lua Script：原子扣減庫存
    private static final String DECRBY_SCRIPT = """
        local stock = tonumber(redis.call('GET', KEYS[1]))
        if stock == nil then return -1 end
        if stock < tonumber(ARGV[1]) then return -2 end
        return redis.call('DECRBY', KEYS[1], ARGV[1])
        """;

    /**
     * 原子扣減庫存
     * @return >= 0 成功剩餘量, -1 商品不存在, -2 庫存不足
     */
    public long decreaseStock(String productId, int quantity) {
        String key = STOCK_PREFIX + productId;
        RedisScript<Long> script = RedisScript.of(DECRBY_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, List.of(key), String.valueOf(quantity));
        log.info("Redis DECRBY: key={}, qty={}, result={}", key, quantity, result);
        return result != null ? result : -1L;
    }

    /**
     * 還原庫存（Saga 補償用，原子操作）
     */
    public long restoreStock(String productId, int quantity) {
        String key = STOCK_PREFIX + productId;
        Long result = redisTemplate.opsForValue().increment(key, quantity);
        log.info("Redis stock restored: productId={}, qty={}, newStock={}", productId, quantity, result);
        return result != null ? result : 0L;
    }

    public void initStock(String productId, int stock) {
        redisTemplate.opsForValue().set(STOCK_PREFIX + productId, String.valueOf(stock));
        log.info("Redis stock initialized: productId={}, stock={}", productId, stock);
    }

    public Long getStock(String productId) {
        Object val = redisTemplate.opsForValue().get(STOCK_PREFIX + productId);
        return val != null ? Long.parseLong(val.toString()) : null;
    }

    /**
     * deductStock — alias for decreaseStock（向後相容）
     */
    public long deductStock(String productId, int quantity) {
        return decreaseStock(productId, quantity);
    }
}
