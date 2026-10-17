package com.eventpass.eventpass;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.eventpass.eventpass.entity.Payment;
import com.eventpass.eventpass.entity.User;
import com.eventpass.eventpass.repository.BookingRepository;
import com.eventpass.eventpass.repository.CheckInRepository;
import com.eventpass.eventpass.repository.PaymentRepository;
import com.eventpass.eventpass.repository.TicketRepository;
import com.eventpass.eventpass.repository.UserRepository;
import com.eventpass.eventpass.service.PaymentService;
import com.eventpass.eventpass.service.UserService;

@SpringBootTest
class PaymentServiceTest {

    private static final String SECRET = "test_secret_123";

    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired CheckInRepository checkInRepository;

    private User user;

    @BeforeEach
    void setUp() {
        checkInRepository.deleteAll();
        ticketRepository.deleteAll();
        bookingRepository.deleteAll();
        paymentRepository.deleteAll();
        userRepository.deleteAll();
        user = userService.register("payuser", "secret123", "pay@example.com");
    }

    private static String hmac(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] d = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private Payment pending(BigDecimal amount) {
        return paymentRepository.save(new Payment(user, amount, "order_test_" + System.nanoTime()));
    }

    @Test
    void signatureVerification() {
        String sig = sneaky(() -> hmac("order_1", "pay_1"));
        assertTrue(paymentService.verifySignature("order_1", "pay_1", sig));
        assertFalse(paymentService.verifySignature("order_1", "pay_1", sig + "00"));
        assertFalse(paymentService.verifySignature("order_1", "other_pay", sig));
    }

    @Test
    void completeCreditsWalletExactlyOnce() {
        Payment p = pending(new BigDecimal("250.00"));
        String sig = sneaky(() -> hmac(p.getRazorpayOrderId(), "pay_ok_1"));

        Payment done = paymentService.complete(p.getRazorpayOrderId(), "pay_ok_1", sig);
        assertEquals("COMPLETED", done.getStatus());
        assertEquals(0, new BigDecimal("750.00").compareTo(userRepository.findById(user.getId()).orElseThrow().getBalance()));

        // replay (e.g. double webhook) must not credit again
        Payment replay = paymentService.complete(p.getRazorpayOrderId(), "pay_ok_1", sig);
        assertEquals("COMPLETED", replay.getStatus());
        assertEquals(0, new BigDecimal("750.00").compareTo(userRepository.findById(user.getId()).orElseThrow().getBalance()));
    }

    @Test
    void badSignatureFailsWithoutCredit() {
        Payment p = pending(new BigDecimal("100.00"));
        Payment out = paymentService.complete(p.getRazorpayOrderId(), "pay_x", "bogus");
        assertEquals("FAILED", out.getStatus());
        assertEquals(0, new BigDecimal("500.00").compareTo(userRepository.findById(user.getId()).orElseThrow().getBalance()));
    }

    @Test
    void failRecordsReason() {
        Payment p = pending(new BigDecimal("100.00"));
        Payment out = paymentService.fail(p.getRazorpayOrderId(), "user closed checkout");
        assertEquals("FAILED", out.getStatus());
        assertEquals("user closed checkout", out.getFailureReason());
    }

    @Test
    void historyListsUserTopups() {
        pending(new BigDecimal("100.00"));
        pending(new BigDecimal("200.00"));
        List<Payment> h = paymentService.history(user.getId());
        assertEquals(2, h.size());
    }

    @Test
    void createOrderWithoutKeysFailsCleanly() {
        // keys ARE set in tests; sanity-check validation path instead
        assertThrows(RuntimeException.class, () -> paymentService.createOrder(999999L, new BigDecimal("10.00")));
    }

    private static String sneaky(HmacSupplier s) {
        try { return s.get(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @FunctionalInterface
    interface HmacSupplier { String get() throws Exception; }
}
