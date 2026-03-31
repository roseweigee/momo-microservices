package com.demo.payment.controller;

import com.demo.payment.model.PaymentEntity;
import com.demo.payment.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment API", description = "付款管理")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @Operation(summary = "查詢付款狀態")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentEntity> getByOrderId(@PathVariable String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
