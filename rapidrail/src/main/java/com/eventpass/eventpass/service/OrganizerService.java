package com.eventpass.eventpass.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.OrganizerRepository;
import com.eventpass.eventpass.util.PasswordUtil;

@Service
public class OrganizerService {

    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private EventRepository eventRepository;

    public List<Organizer> getAll() { return organizerRepository.findAll(); }

    public Organizer getById(Long id) {
        return organizerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organizer not found"));
    }

    @Transactional
    public Organizer create(String name, String email, String organizationName, String accessCode) {
        if (name == null || name.isBlank()) throw new RuntimeException("Organizer name is required");
        if (email == null || email.isBlank()) throw new RuntimeException("Email is required");
        if (organizationName == null || organizationName.isBlank()) throw new RuntimeException("Organization name is required");
        if (accessCode == null || accessCode.length() < 4) throw new RuntimeException("Access code must be at least 4 characters");
        if (organizerRepository.existsByEmail(email.trim())) throw new RuntimeException("Email already registered");
        Organizer o = new Organizer();
        o.setName(name.trim());
        o.setEmail(email.trim());
        o.setOrganizationName(organizationName.trim());
        o.setAccessCodeHash(PasswordUtil.hash(accessCode));
        return organizerRepository.save(o);
    }

    /** Verifies the organizer's access code. Throws on failure. */
    public Organizer authenticate(Long organizerId, String accessCode) {
        Organizer o = getById(organizerId);
        if (accessCode == null || !PasswordUtil.verify(accessCode, o.getAccessCodeHash())) {
            throw new RuntimeException("Invalid organizer credentials");
        }
        return o;
    }

    @Transactional
    public Organizer update(Long id, String name, String organizationName, String accessCode) {
        Organizer o = authenticate(id, accessCode);
        if (name != null && !name.isBlank()) o.setName(name.trim());
        if (organizationName != null && !organizationName.isBlank()) o.setOrganizationName(organizationName.trim());
        return organizerRepository.save(o);
    }

    /**
     * Delete only when the organizer owns no events.
     * Prevents orphaning events/bookings/tickets. Delete the events first
     * (events themselves are blocked while active bookings exist).
     */
    @Transactional
    public void delete(Long id, String accessCode) {
        Organizer o = authenticate(id, accessCode);
        if (!eventRepository.findByOrganizerId(id).isEmpty()) {
            throw new RuntimeException("Cannot delete organizer with existing events. Delete or reassign the events first.");
        }
        organizerRepository.delete(o);
    }
}
