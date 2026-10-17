package com.eventpass.eventpass.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.Payment;
import com.eventpass.eventpass.entity.User;
import com.eventpass.eventpass.repository.PaymentRepository;
import com.eventpass.eventpass.repository.UserRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;

/**
 * Razorpay wallet top-ups (test mode). Adapted from the rapidrail project's
 * payment flow, with two fixes: BigDecimal money (no double) and JDK-only
 * HMAC verification (no extra codec dependency).
 *
 * When no keys are configured the checkout is disabled and the UI falls back
 * to instant demo top-up (POST /api/users/{id}/recharge).
 */
@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    private volatile RazorpayClient client;

    public boolean isEnabled() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    private RazorpayClient getClient() throws Exception {
        if (!isEnabled()) throw new RuntimeException("Razorpay is not configured (RAZORPAY_KEY_ID/RAZORPAY_KEY_SECRET missing)");
        if (client == null) {
            synchronized (this) {
                if (client == null) client = new RazorpayClient(keyId, keySecret);
            }
        }
        return client;
    }

    /** Step 1: create a Razorpay order + local PENDING record. */
    public Payment createOrder(Long userId, BigDecimal amount) throws Exception {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be > 0");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        JSONObject req = new JSONObject();
        req.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue()); // paise
        req.put("currency", "INR");
        req.put("receipt", "eventpass_" + userId + "_" + System.currentTimeMillis());

        Order order;
        try {
            order = getClient().orders.create(req);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage());
        }

        Payment p = new Payment(user, amount.setScale(2, RoundingMode.HALF_UP), order.get("id").toString());
        return paymentRepository.save(p);
    }

    /** Step 2: verify Razorpay signature (HMAC-SHA256 of orderId|paymentId). */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Step 3: verify + mark COMPLETED and credit the wallet exactly once.
     * Re-verifying an already COMPLETED order returns it unchanged (no double credit).
     */
    @Transactional
    public Payment complete(String orderId, String paymentId, String signature) {
        Payment p = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));
        if ("COMPLETED".equals(p.getStatus())) return p; // idempotent replay
        if (!"PENDING".equals(p.getStatus())) throw new RuntimeException("Payment is " + p.getStatus() + ", cannot complete");
        if (!verifySignature(orderId, paymentId, signature)) {
            p.setStatus("FAILED");
            p.setFailureReason("Signature verification failed");
            p.setUpdatedAt(LocalDateTime.now());
            return paymentRepository.save(p);
        }
        p.setRazorpayPaymentId(paymentId);
        p.setRazorpaySignature(signature);
        p.setStatus("COMPLETED");
        p.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(p);
        userService.rechargeWallet(p.getUser().getId(), p.getAmount());
        return p;
    }

    @Transactional
    public Payment fail(String orderId, String reason) {
        Payment p = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));
        if (!"COMPLETED".equals(p.getStatus())) {
            p.setStatus("FAILED");
            p.setFailureReason(reason);
            p.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(p);
        }
        return p;
    }

    public String statusOf(String orderId) {
        return paymentRepository.findByRazorpayOrderId(orderId).map(Payment::getStatus).orElse("NOT_FOUND");
    }

    public List<Payment> history(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
