package com.eventpass.eventpass;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.OrganizerRepository;
import com.eventpass.eventpass.util.PasswordUtil;

/**
 * Seeds demo organizers + events on first boot so the app is usable on a
 * fresh clone. Only runs when no events exist; never overwrites user data.
 * Demo organizer access codes: "organizer123" (both seed organizers).
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(OrganizerRepository organizerRepo, EventRepository eventRepo) {
        return args -> {
            if (eventRepo.count() > 0) return;

            Organizer rhythm = new Organizer();
            rhythm.setName("Aarav Sharma");
            rhythm.setEmail("aarav@rhythmnights.in");
            rhythm.setOrganizationName("Rhythm Nights");
            rhythm.setAccessCodeHash(PasswordUtil.hash("organizer123"));
            rhythm = organizerRepo.save(rhythm);

            Organizer skills = new Organizer();
            skills.setName("Meera Iyer");
            skills.setEmail("meera@skillHum.in");
            skills.setOrganizationName("SkillHum Academy");
            skills.setAccessCodeHash(PasswordUtil.hash("organizer123"));
            skills = organizerRepo.save(skills);

            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

            saveEvent(eventRepo, "Monsoon Beats Concert", "Live indie concert under the monsoon sky. Gates open 1 hour early.",
                    "Concert", "Phoenix Grounds, Mumbai", now.plusDays(3).withHour(19).withMinute(0),
                    new BigDecimal("499.00"), 200, rhythm);

            saveEvent(eventRepo, "Pottery Workshop", "Beginner-friendly clay workshop. All materials included, take your pot home.",
                    "Workshop", "Kala Studio, Bandra", now.plusDays(5).withHour(11).withMinute(0),
                    new BigDecimal("899.00"), 40, skills);

            saveEvent(eventRepo, "Startup Funding Seminar", "Local founders and angels on raising your first round. Q&A included.",
                    "Seminar", "Innovation Hub, Andheri", now.plusDays(7).withHour(15).withMinute(0),
                    new BigDecimal("199.00"), 150, skills);

            saveEvent(eventRepo, "Jazz by the Lake", "Evening jazz ensemble with food stalls and sunset views.",
                    "Concert", "Powai Lakefront", now.plusDays(10).withHour(18).withMinute(30),
                    new BigDecimal("349.00"), 120, rhythm);

            saveEvent(eventRepo, "Python for Beginners", "Hands-on coding workshop. Bring a laptop; no experience needed.",
                    "Workshop", "City Library Hall, Thane", now.plusDays(12).withHour(10).withMinute(0),
                    new BigDecimal("299.00"), 60, skills);
        };
    }

    private void saveEvent(EventRepository repo, String title, String desc, String category,
                           String venue, LocalDateTime when, BigDecimal price, int capacity, Organizer org) {
        Event e = new Event();
        e.setTitle(title);
        e.setDescription(desc);
        e.setCategory(category);
        e.setVenue(venue);
        e.setEventDateTime(when);
        e.setTicketPrice(price);
        e.setTotalCapacity(capacity);
        e.setAvailableSeats(capacity);
        e.setOrganizer(org);
        repo.save(e);
    }
}
