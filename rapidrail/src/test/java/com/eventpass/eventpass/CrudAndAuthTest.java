package com.eventpass.eventpass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.repository.OrganizerRepository;
import com.eventpass.eventpass.service.EventService;
import com.eventpass.eventpass.service.OrganizerService;
import com.eventpass.eventpass.util.PasswordUtil;

@SpringBootTest
class CrudAndAuthTest {

    @Autowired OrganizerService organizerService;
    @Autowired EventService eventService;
    @Autowired OrganizerRepository organizerRepository;

    @Test
    void organizerCrudWithServerSideAuth() {
        Organizer o = organizerService.create("Jane", "jane@example.com", "Jane Events", "code1234");
        assertTrue(o.getId() != null);
        assertEquals("jane@example.com", organizerService.getById(o.getId()).getEmail());

        // wrong code rejected
        assertThrows(RuntimeException.class, () -> organizerService.authenticate(o.getId(), "wrong"));
        // right code works
        organizerService.authenticate(o.getId(), "code1234");

        Organizer updated = organizerService.update(o.getId(), "Jane Updated", null, "code1234");
        assertEquals("Jane Updated", updated.getName());

        // secrets are hashed, never plaintext
        assertTrue(!"code1234".equals(updated.getAccessCodeHash()));
        assertTrue(PasswordUtil.verify("code1234", updated.getAccessCodeHash()));

        organizerService.delete(o.getId(), "code1234");
        assertThrows(RuntimeException.class, () -> organizerService.getById(o.getId()));
    }

    @Test
    void duplicateOrganizerEmailRejected() {
        organizerService.create("A", "dup@example.com", "Org A", "code1234");
        assertThrows(RuntimeException.class,
                () -> organizerService.create("B", "dup@example.com", "Org B", "code1234"));
    }

    @Test
    void eventPosterImageRoundTrip() {
        com.eventpass.eventpass.entity.Organizer o =
                organizerService.create("Pic", "pic@example.com", "Pic Org", "code1234");
        com.eventpass.eventpass.entity.Event e = new com.eventpass.eventpass.entity.Event();
        e.setTitle("Poster Gig");
        e.setCategory("Concert");
        e.setVenue("Hall");
        e.setEventDateTime(java.time.LocalDateTime.now().plusDays(3));
        e.setTicketPrice(new java.math.BigDecimal("99.00"));
        e.setTotalCapacity(20);
        e.setImageUrl("/img/posters/monsoon-beats.svg");
        com.eventpass.eventpass.entity.Event saved =
                eventService.create(e, o.getId(), "code1234");
        assertEquals("/img/posters/monsoon-beats.svg", saved.getImageUrl());

        com.eventpass.eventpass.entity.Event patch = new com.eventpass.eventpass.entity.Event();
        patch.setImageUrl("https://example.com/p.png");
        assertEquals("https://example.com/p.png",
                eventService.update(saved.getId(), patch, o.getId(), "code1234").getImageUrl());
    }

    @Test
    void adminBypassesOwnershipWhileOthersAreBlocked() {
        com.eventpass.eventpass.entity.Organizer owner =
                organizerService.create("Owner", "owner@example.com", "Owner Org", "code1234");
        com.eventpass.eventpass.entity.Organizer stranger =
                organizerService.create("Stranger", "stranger@example.com", "Stranger Org", "code1234");
        com.eventpass.eventpass.entity.Organizer admin =
                organizerService.create("Root", "root@example.com", "Root Org", "code1234");
        admin.setAdmin(true);
        admin = organizerRepository.save(admin);

        com.eventpass.eventpass.entity.Event e = new com.eventpass.eventpass.entity.Event();
        e.setTitle("Owned Gig");
        e.setCategory("Concert");
        e.setVenue("Hall");
        e.setEventDateTime(java.time.LocalDateTime.now().plusDays(3));
        e.setTicketPrice(new java.math.BigDecimal("50.00"));
        e.setTotalCapacity(30);
        e = eventService.create(e, owner.getId(), "code1234");

        com.eventpass.eventpass.entity.Event patch = new com.eventpass.eventpass.entity.Event();
        patch.setVenue("New Hall");

        // stranger is forbidden
        final Long eventId = e.getId();
        final Long strangerId = stranger.getId();
        assertThrows(RuntimeException.class,
                () -> eventService.update(eventId, patch, strangerId, "code1234"));

        // admin can edit and delete anything
        assertEquals("New Hall",
                eventService.update(eventId, patch, admin.getId(), "code1234").getVenue());
        eventService.delete(eventId, admin.getId(), "code1234");
        assertThrows(RuntimeException.class, () -> eventService.getById(eventId));
    }
}
