package com.eventpass.eventpass.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.eventpass.entity.CheckIn;
import com.eventpass.eventpass.service.CheckInException;
import com.eventpass.eventpass.service.CheckInResult;
import com.eventpass.eventpass.service.CheckInService;

/**
 * Organizer check-in (scan simulation). POST consumes the ticket exactly once;
 * GET previews/reads history without mutating anything. There is intentionally
 * no PUT/DELETE: entry history is append-only.
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

    @Autowired
    private CheckInService checkInService;

    /**
     * POST /api/checkin  body: { qrToken, eventId, organizerId, organizerCode }
     * Success (first scan): 200 { result: VALID, checkIn, ... }
     * Re-scan: 409 ALREADY_USED · cancelled: 422 CANCELLED ·
     * wrong event: 422 WRONG_EVENT · unknown token: 404 INVALID.
     */
    @PostMapping
    public ResponseEntity<?> checkIn(@RequestBody Map<String, Object> body) {
        try {
            if (body.get("qrToken") == null || body.get("eventId") == null
                    || body.get("organizerId") == null || body.get("organizerCode") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "qrToken, eventId, organizerId and organizerCode are required"));
            }
            String qrToken = body.get("qrToken").toString().trim();
            Long eventId = ((Number) body.get("eventId")).longValue();
            Long organizerId = ((Number) body.get("organizerId")).longValue();
            String organizerCode = body.get("organizerCode").toString();

            CheckIn checkIn = checkInService.checkIn(qrToken, eventId, organizerId, organizerCode);
            return ResponseEntity.ok(Map.of(
                    "result", CheckInResult.VALID.name(),
                    "message", "Entry permitted",
                    "checkIn", checkIn,
                    "ticket", checkIn.getTicket()));
        } catch (CheckInException e) {
            return switch (e.getResult()) {
                case ALREADY_USED -> ResponseEntity.status(409).body(Map.of("result", "ALREADY_USED", "message", "Entry denied: " + e.getMessage()));
                case CANCELLED -> ResponseEntity.status(422).body(Map.of("result", "CANCELLED", "message", "Entry denied: " + e.getMessage()));
                case WRONG_EVENT -> ResponseEntity.status(422).body(Map.of("result", "WRONG_EVENT", "message", "Entry denied: " + e.getMessage()));
                case INVALID -> ResponseEntity.status(404).body(Map.of("result", "INVALID", "message", "Entry denied: " + e.getMessage()));
                default -> ResponseEntity.badRequest().body(Map.of("result", e.getResult().name(), "message", e.getMessage()));
            };
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Check-in failed" : e.getMessage();
            if (msg.contains("Invalid organizer credentials")) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    // Read-only preview: does NOT consume the ticket.
    // GET /api/checkin/preview?qrToken=...&eventId=...
    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam String qrToken,
                                     @RequestParam(required = false) Long eventId) {
        CheckInResult result = checkInService.preview(qrToken, eventId);
        if (result == CheckInResult.INVALID) {
            return ResponseEntity.status(404).body(Map.of("result", result.name()));
        }
        return ResponseEntity.ok(Map.of("result", result.name()));
    }

    @GetMapping("/event/{eventId}")
    public List<CheckIn> historyByEvent(@PathVariable Long eventId) {
        return checkInService.getHistoryByEvent(eventId);
    }

    @GetMapping("/organizer/{organizerId}")
    public List<CheckIn> historyByOrganizer(@PathVariable Long organizerId) {
        return checkInService.getHistoryByOrganizer(organizerId);
    }

    @GetMapping
    public List<CheckIn> all() { return checkInService.getAll(); }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<?> byTicket(@PathVariable String ticketId) {
        return checkInService.getByTicket(ticketId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
