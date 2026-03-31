package com.demo.inventory.config;

import com.demo.inventory.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStockInitializer implements ApplicationRunner {

    private final WarehouseStockRepository stockRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_PREFIX = "wh_stock:";

    @Override
    public void run(ApplicationArguments args) {
        log.info("初始化 Redis 倉庫庫存...");
        stockRepo.findAll().forEach(stock -> {
            String key = STOCK_PREFIX + stock.getWarehouseId() + ":" + stock.getProductId();
            redisTemplate.opsForValue().set(key, String.valueOf(stock.getQuantity()));
            log.info("Redis 初始化: key={}, quantity={}", key, stock.getQuantity());
        });
        log.info("Redis 倉庫庫存初始化完成");
    }
}
