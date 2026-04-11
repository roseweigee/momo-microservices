package com.demo.order.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class SagaLoggingAspect {

    @Around("execution(* com.demo.order.saga.SagaOrchestrator.*(..))")
    public Object logSagaExecution(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        String orderId = extractOrderId(pjp.getArgs());
        long start = System.currentTimeMillis();

        log.info("[SAGA-LOG] step={} orderId={} status=STARTED ts={}",
                method, orderId, Instant.now());
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[SAGA-LOG] step={} orderId={} status=COMPLETED elapsed={}ms ts={}",
                    method, orderId, elapsed, Instant.now());
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[SAGA-LOG] step={} orderId={} status=FAILED elapsed={}ms error={} ts={}",
                    method, orderId, elapsed, e.getMessage(), Instant.now());
            throw e;
        }
    }

    private String extractOrderId(Object[] args) {
        if (args == null || args.length == 0) return "unknown";
        // 第一個 String 參數通常是 orderId
        return Arrays.stream(args)
                .filter(a -> a instanceof String)
                .map(Object::toString)
                .findFirst()
                .orElse("unknown");
    }
}