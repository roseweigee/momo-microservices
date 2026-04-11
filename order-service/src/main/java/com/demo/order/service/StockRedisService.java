package com.demo.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STOCK_PREFIX = "stock:";

    private static final String DECRBY_SCRIPT = """
        local stock = tonumber(redis.call('GET', KEYS[1]))
        if stock == nil then return -1 end
        if stock < tonumber(ARGV[1]) then return -2 end
        return redis.call('DECRBY', KEYS[1], ARGV[1])
        """;

    public long decreaseStock(String productId, int quantity) {
        String key = STOCK_PREFIX + productId;
        RedisScript<Long> script = RedisScript.of(DECRBY_SCRIPT, Long.class);
        Long result = redisTemplate.execute(
                script,
                new org.springframework.data.redis.serializer.StringRedisSerializer(),
                new org.springframework.data.redis.serializer.GenericToStringSerializer<>(Long.class),
                List.of(key),
                String.valueOf(quantity)
        );
        log.info("Redis DECRBY: key={}, qty={}, result={}", key, quantity, result);
        return result != null ? result : -1L;
    }

    public long restoreStock(String productId, int quantity) {
        String key = STOCK_PREFIX + productId;
        Long result = redisTemplate.execute(
                (RedisCallback<Long>) conn ->
                        conn.stringCommands().incrBy(
                                key.getBytes(StandardCharsets.UTF_8),
                                quantity
                        )
        );
        log.info("Redis stock restored: productId={}, qty={}, newStock={}", productId, quantity, result);
        return result != null ? result : 0L;
    }

    public void initStock(String productId, int stock) {
        String key = STOCK_PREFIX + productId;
        redisTemplate.execute(
                (RedisCallback<Void>) conn -> {
                    conn.stringCommands().set(
                            key.getBytes(StandardCharsets.UTF_8),
                            String.valueOf(stock).getBytes(StandardCharsets.UTF_8)
                    );
                    return null;
                }
        );
        log.info("Redis stock initialized: productId={}, stock={}", productId, stock);
    }

    public Long getStock(String productId) {
        Object val = redisTemplate.opsForValue().get(STOCK_PREFIX + productId);
        if (val == null) return null;
        String strVal = val.toString().replace("\"", "");
        return Long.parseLong(strVal);
    }

    public long deductStock(String productId, int quantity) {
        return decreaseStock(productId, quantity);
    }
}
