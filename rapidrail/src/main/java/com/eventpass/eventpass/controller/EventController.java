package com.eventpass.eventpass.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.service.EventService;

/**
 * Event CRUD. Writes require organizer credentials (X-Organizer-Id +
 * X-Organizer-Code headers) verified server-side; the event must belong to
 * the calling organizer. Delete is blocked while CONFIRMED bookings exist.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Event event,
                                    @RequestHeader(value = "X-Organizer-Id", required = false) Long organizerId,
                                    @RequestHeader(value = "X-Organizer-Code", required = false) String organizerCode) {
        try {
            if (organizerId == null || organizerCode == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Organizer credentials (X-Organizer-Id, X-Organizer-Code) are required"));
            }
            return ResponseEntity.status(201).body(eventService.create(event, organizerId, organizerCode));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid organizer credentials")) {
                return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // READ ALL with optional filters: ?category= ?organizerId= ?upcoming=true ?search=
    @GetMapping
    public List<Event> list(@RequestParam(required = false) String category,
                            @RequestParam(required = false) Long organizerId,
                            @RequestParam(required = false, defaultValue = "false") boolean upcoming,
                            @RequestParam(required = false) String search) {
        if (category != null && !category.isBlank()) return eventService.getByCategory(category);
        if (organizerId != null) return eventService.getByOrganizer(organizerId);
        if (upcoming) return eventService.getUpcoming();
        if (search != null && !search.isBlank()) return eventService.search(search);
        return eventService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(eventService.getById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Recommended for a user: GET /api/events/recommended?userId= (optional)
    @GetMapping("/recommended")
    public List<Event> recommended(@RequestParam(required = false) Long userId) {
        return eventService.getRecommended(userId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Event event,
                                    @RequestHeader(value = "X-Organizer-Id", required = false) Long organizerId,
                                    @RequestHeader(value = "X-Organizer-Code", required = false) String organizerCode) {
        try {
            if (organizerId == null || organizerCode == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Organizer credentials (X-Organizer-Id, X-Organizer-Code) are required"));
            }
            return ResponseEntity.ok(eventService.update(id, event, organizerId, organizerCode));
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Invalid organizer credentials")) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            if (msg.contains("Not authorized")) {
                return ResponseEntity.status(403).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader(value = "X-Organizer-Id", required = false) Long organizerId,
                                    @RequestHeader(value = "X-Organizer-Code", required = false) String organizerCode) {
        try {
            if (organizerId == null || organizerCode == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Organizer credentials (X-Organizer-Id, X-Organizer-Code) are required"));
            }
            eventService.delete(id, organizerId, organizerCode);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Invalid organizer credentials")) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            if (msg.contains("Not authorized")) {
                return ResponseEntity.status(403).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }
}
