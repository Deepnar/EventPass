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
import org.springframework.web.bind.annotation.RestController;

import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.service.OrganizerService;

/**
 * Organizer CRUD. Create is open (registration); update/delete require the
 * organizer's own access code via the X-Organizer-Code header so one
 * organizer cannot modify another. Delete is blocked while events exist.
 */
@RestController
@RequestMapping("/api/organizers")
public class OrganizerController {

    @Autowired
    private OrganizerService organizerService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        try {
            Organizer o = organizerService.create(
                    body.get("name"), body.get("email"),
                    body.get("organizationName"), body.get("accessCode"));
            return ResponseEntity.status(201).body(o);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<Organizer> list() { return organizerService.getAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(organizerService.getById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, String> body,
                                    @RequestHeader(value = "X-Organizer-Code", required = false) String accessCode) {
        try {
            if (accessCode == null) return ResponseEntity.status(401).body(Map.of("error", "X-Organizer-Code header is required"));
            return ResponseEntity.ok(organizerService.update(id, body.get("name"), body.get("organizationName"), accessCode));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid organizer credentials")) {
                return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader(value = "X-Organizer-Code", required = false) String accessCode) {
        try {
            if (accessCode == null) return ResponseEntity.status(401).body(Map.of("error", "X-Organizer-Code header is required"));
            organizerService.delete(id, accessCode);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid organizer credentials")) {
                return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/organizers/{id}/verify  body: { accessCode } — used by the organizer login screen.
    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verify(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Organizer o = organizerService.authenticate(id, body.get("accessCode"));
            return ResponseEntity.ok(o);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid organizer credentials"));
        }
    }
}
