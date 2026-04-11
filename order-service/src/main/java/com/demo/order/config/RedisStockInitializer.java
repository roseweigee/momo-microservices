package com.demo.order.config;

import com.demo.order.repository.ProductRepository;
import com.demo.order.service.StockRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStockInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final StockRedisService stockRedisService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("初始化 Redis 商品庫存...");
        productRepository.findAll().forEach(product -> {
            stockRedisService.initStock(product.getProductId(), product.getStock());
            log.info("Redis 初始化: productId={}, stock={}", product.getProductId(), product.getStock());
        });
        log.info("Redis 商品庫存初始化完成");
    }
}
