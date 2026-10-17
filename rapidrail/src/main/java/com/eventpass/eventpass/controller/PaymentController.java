package com.eventpass.eventpass.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.eventpass.entity.Payment;
import com.eventpass.eventpass.service.PaymentService;

/**
 * Razorpay wallet top-ups. Flow: POST /initiate → Razorpay checkout.js →
 * POST /verify (credits wallet once) or POST /failure. Without configured
 * keys, /config reports enabled=false and the UI uses instant demo top-up.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    // UI probe: { enabled, keyId? }
    @GetMapping("/config")
    public ResponseEntity<?> config() {
        if (!paymentService.isEnabled()) return ResponseEntity.ok(Map.of("enabled", false));
        return ResponseEntity.ok(Map.of("enabled", true, "keyId", paymentService.getKeyId()));
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@RequestBody Map<String, Object> body) {
        try {
            if (body.get("userId") == null || body.get("amount") == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "userId and amount are required"));
            }
            Long userId = ((Number) body.get("userId")).longValue();
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            Payment p = paymentService.createOrder(userId, amount);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "orderId", p.getRazorpayOrderId(),
                    "paymentId", p.getId(),
                    "amount", p.getAmount(),
                    "keyId", paymentService.getKeyId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        try {
            String orderId = body.get("orderId");
            String paymentId = body.get("paymentId");
            String signature = body.get("signature");
            if (orderId == null || paymentId == null || signature == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "orderId, paymentId and signature are required"));
            }
            Payment p = paymentService.complete(orderId, paymentId, signature);
            if (!"COMPLETED".equals(p.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Signature verification failed"));
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Wallet credited",
                    "amount", p.getAmount(), "status", p.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/failure")
    public ResponseEntity<?> failure(@RequestBody Map<String, String> body) {
        try {
            if (body.get("orderId") == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "orderId is required"));
            }
            Payment p = paymentService.fail(body.get("orderId"), body.get("failureReason"));
            return ResponseEntity.ok(Map.of("success", true, "status", p.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<?> status(@PathVariable String orderId) {
        String s = paymentService.statusOf(orderId);
        if ("NOT_FOUND".equals(s)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", s));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<?> history(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("success", true, "payments", paymentService.history(userId)));
    }
}
