package com.eventpass.eventpass.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.eventpass.entity.Ticket;
import com.eventpass.eventpass.service.CheckInResult;
import com.eventpass.eventpass.service.CheckInService;
import com.eventpass.eventpass.service.QrService;
import com.eventpass.eventpass.service.TicketService;

/**
 * Ticket reads. Tickets are created through booking and change state only
 * through check-in / booking cancellation, so there is intentionally no
 * POST/PUT here. DELETE is limited to already-CANCELLED tickets.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private QrService qrService;

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getOne(@PathVariable String ticketId) {
        try {
            Ticket t = ticketService.getById(ticketId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("ticket", t);
            resp.put("qrCode", qrService.generateQRCode(t.getQrToken()));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Ticket not found"));
        }
    }

    // Read-only lookup by QR token. Does NOT mark the ticket used.
    @GetMapping("/qr/{qrToken}")
    public ResponseEntity<?> byQrToken(@PathVariable String qrToken) {
        try {
            Ticket t = ticketService.getByQrToken(qrToken);
            Map<String, Object> resp = new HashMap<>();
            resp.put("ticket", t);
            resp.put("qrCode", qrService.generateQRCode(t.getQrToken()));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("valid", false, "error", "Invalid QR token"));
        }
    }

    @GetMapping("/booking/{bookingId}")
    public List<Ticket> byBooking(@PathVariable String bookingId) {
        return ticketService.getByBooking(bookingId);
    }

    @GetMapping("/event/{eventId}")
    public List<Ticket> byEvent(@PathVariable Long eventId) {
        return ticketService.getByEvent(eventId);
    }

    @GetMapping
    public List<Ticket> all() { return ticketService.getAll(); }

    // Read-only validation preview (no state change). Use POST /api/checkin to consume.
    @GetMapping("/validate/{qrToken}")
    public ResponseEntity<?> validate(@PathVariable String qrToken) {
        try {
            Ticket t = ticketService.getByQrToken(qrToken);
            CheckInResult result = checkInService.preview(qrToken, null);
            return ResponseEntity.ok(Map.of("result", result.name(), "ticket", t));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("result", "INVALID", "error", "Invalid QR token"));
        }
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<?> delete(@PathVariable String ticketId) {
        try {
            ticketService.delete(ticketId);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
