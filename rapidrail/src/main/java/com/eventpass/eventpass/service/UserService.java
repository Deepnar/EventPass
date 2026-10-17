package com.eventpass.eventpass.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.User;
import com.eventpass.eventpass.repository.BookingRepository;
import com.eventpass.eventpass.repository.UserRepository;
import com.eventpass.eventpass.util.PasswordUtil;

@Service
public class UserService {

    private static final BigDecimal SIGNUP_BONUS = new BigDecimal("500.00");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    public User register(String username, String password, String email) {
        if (username == null || username.isBlank()) throw new RuntimeException("Username is required");
        if (password == null || password.isBlank()) throw new RuntimeException("Password is required");
        if (email == null || email.isBlank()) throw new RuntimeException("Email is required");
        if (userRepository.existsByUsername(username)) throw new RuntimeException("Username already taken");
        User u = new User(username.trim(), PasswordUtil.hash(password), email.trim(), SIGNUP_BONUS);
        return userRepository.save(u);
    }

    /** Returns the user on success, null on bad credentials (keeps old controller contract). */
    public User login(String username, String password) {
        if (username == null || password == null) return null;
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent() && PasswordUtil.verify(password, user.get().getPassword())) {
            return user.get();
        }
        return null;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User rechargeWallet(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be > 0");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBalance(user.getBalance().add(amount).setScale(2, RoundingMode.HALF_UP));
        return userRepository.save(user);
    }

    public User update(Long id, String email) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (email != null && !email.isBlank()) user.setEmail(email.trim());
        return userRepository.save(user);
    }

    /** Delete only when the user has no CONFIRMED bookings (history is preserved otherwise). */
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean hasActive = bookingRepository.findByUserIdOrderByBookingTimeDesc(id).stream()
                .anyMatch(b -> "CONFIRMED".equals(b.getStatus()));
        if (hasActive) throw new RuntimeException("Cannot delete user with active (CONFIRMED) bookings. Cancel them first.");
        userRepository.delete(user);
    }
}
