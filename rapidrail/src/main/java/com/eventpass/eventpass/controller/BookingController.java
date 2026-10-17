package com.eventpass.eventpass.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.eventpass.entity.Booking;
import com.eventpass.eventpass.entity.Ticket;
import com.eventpass.eventpass.service.BookingService;
import com.eventpass.eventpass.service.QrService;
import com.eventpass.eventpass.service.TicketService;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private QrService qrService;

    // Book tickets: POST /api/bookings  body: { userId, eventId, quantity }
    // Returns { booking, tickets, qrCodes: { ticketId: base64png } }
    @PostMapping
    public ResponseEntity<?> book(@RequestBody Map<String, Object> request) {
        try {
            if (request.get("userId") == null || request.get("eventId") == null || request.get("quantity") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "userId, eventId and quantity are required"));
            }
            Long userId = ((Number) request.get("userId")).longValue();
            Long eventId = ((Number) request.get("eventId")).longValue();
            Integer quantity = ((Number) request.get("quantity")).intValue();

            Booking booking = bookingService.bookTickets(userId, eventId, quantity);
            return ResponseEntity.status(201).body(bookingWithTickets(booking.getBookingId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get one booking with its tickets + QR codes (booking confirmation view)
    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getOne(@PathVariable String bookingId) {
        try {
            return ResponseEntity.ok(bookingWithTickets(bookingId));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Booking not found"));
        }
    }

    @GetMapping("/user/{userId}")
    public List<Booking> userHistory(@PathVariable Long userId) {
        return bookingService.getByUser(userId);
    }

    @GetMapping("/event/{eventId}")
    public List<Booking> eventBookings(@PathVariable Long eventId) {
        return bookingService.getByEvent(eventId);
    }

    @GetMapping
    public List<Booking> all() { return bookingService.getAll(); }

    // Cancel a booking (restores seats + refunds exactly once)
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String bookingId) {
        try {
            Booking b = bookingService.cancel(bookingId);
            return ResponseEntity.ok(bookingWithTickets(b.getBookingId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Hard delete only for CANCELLED bookings (safe cleanup).
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<?> delete(@PathVariable String bookingId) {
        try {
            bookingService.delete(bookingId);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> bookingWithTickets(String bookingId) {
        Booking booking = bookingService.getById(bookingId);
        List<Ticket> tickets = ticketService.getByBooking(bookingId);
        Map<String, String> qrCodes = new HashMap<>();
        for (Ticket t : tickets) {
            qrCodes.put(t.getTicketId(), qrService.generateQRCode(t.getQrToken()));
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("booking", booking);
        resp.put("tickets", tickets);
        resp.put("qrCodes", qrCodes);
        return resp;
    }
}
