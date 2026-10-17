package com.eventpass.eventpass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.OrganizerRepository;
import com.eventpass.eventpass.util.PasswordUtil;

/**
 * Seeds demo organizers + events on first boot, and backfills any missing
 * demo events on later boots (idempotent: matched by email/title, never
 * overwrites user data). Demo organizer access codes: "organizer123".
 */
@Configuration
public class DataSeeder {

    private record SeedEvent(String title, String desc, String category, String venue,
                             int daysOut, int hour, int minute, String price, int capacity) {}

    @Bean
    CommandLineRunner seed(OrganizerRepository organizerRepo, EventRepository eventRepo) {
        return args -> {
            Organizer rhythm = ensureOrganizer(organizerRepo, "Aarav Sharma", "aarav@rhythmnights.in", "Rhythm Nights");
            Organizer skillHum = ensureOrganizer(organizerRepo, "Meera Iyer", "meera@skillhum.in", "SkillHum Academy");
            Organizer cityArts = ensureOrganizer(organizerRepo, "Kabir Rao", "kabir@cityarts.in", "CityArts Collective");

            List<SeedEvent> seeds = List.of(
                new SeedEvent("Monsoon Beats Concert", "Live indie concert under the monsoon sky. Gates open 1 hour early.",
                        "Concert", "Phoenix Grounds, Mumbai", 3, 19, 0, "499.00", 200),
                new SeedEvent("Jazz by the Lake", "Evening jazz ensemble with food stalls and sunset views.",
                        "Concert", "Powai Lakefront", 10, 18, 30, "349.00", 120),
                new SeedEvent("Sufi Night Unplugged", "An intimate acoustic sufi evening with chai and stars.",
                        "Concert", "Amphitheatre, Juhu", 15, 20, 0, "299.00", 150),
                new SeedEvent("Rock the Fort", "Local rock bands battle it out at the historic fort gates.",
                        "Concert", "Vasai Fort Grounds", 21, 17, 0, "399.00", 300),
                new SeedEvent("Pottery Workshop", "Beginner-friendly clay workshop. All materials included, take your pot home.",
                        "Workshop", "Kala Studio, Bandra", 5, 11, 0, "899.00", 40),
                new SeedEvent("Python for Beginners", "Hands-on coding workshop. Bring a laptop; no experience needed.",
                        "Workshop", "City Library Hall, Thane", 12, 10, 0, "299.00", 60),
                new SeedEvent("Street Photography Walk", "Guided photo walk through old-city lanes. Any camera works, phones welcome.",
                        "Workshop", "CST Heritage Precinct", 8, 7, 30, "249.00", 25),
                new SeedEvent("Baking Basics Masterclass", "Bake your first sourdough and take-home dessert box. Aprons provided.",
                        "Workshop", "Crumb & Co., Powai", 17, 14, 0, "1099.00", 30),
                new SeedEvent("Startup Funding Seminar", "Local founders and angels on raising your first round. Q&A included.",
                        "Seminar", "Innovation Hub, Andheri", 7, 15, 0, "199.00", 150),
                new SeedEvent("Personal Finance 101", "Budgeting, index funds and tax-saving for first-time earners.",
                        "Seminar", "Community Hall, Mulund", 13, 11, 0, "149.00", 100),
                new SeedEvent("Comedy Open Mic Night", "Ten comics, one mic, zero mercy. Audience vote picks the winner.",
                        "Other", "Laugh Loft, Versova", 6, 20, 30, "249.00", 80),
                new SeedEvent("Sunday Flea & Food Fest", "60+ stalls: thrift, vinyl, street food and live buskers all day.",
                        "Other", "Mhada Grounds, Malad", 19, 12, 0, "99.00", 500)
            );

            Organizer[] owners = { rhythm, rhythm, rhythm, rhythm,
                    skillHum, skillHum, cityArts, cityArts,
                    skillHum, cityArts, cityArts, cityArts };

            List<Event> existing = eventRepo.findAll();
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            for (int i = 0; i < seeds.size(); i++) {
                SeedEvent s = seeds.get(i);
                boolean already = existing.stream().anyMatch(e -> s.title().equalsIgnoreCase(e.getTitle()));
                if (already) continue;
                Event e = new Event();
                e.setTitle(s.title());
                e.setDescription(s.desc());
                e.setCategory(s.category());
                e.setVenue(s.venue());
                e.setEventDateTime(now.plusDays(s.daysOut()).withHour(s.hour()).withMinute(s.minute()));
                e.setTicketPrice(new BigDecimal(s.price()));
                e.setTotalCapacity(s.capacity());
                e.setAvailableSeats(s.capacity());
                e.setOrganizer(owners[i]);
                eventRepo.save(e);
            }
        };
    }

    private Organizer ensureOrganizer(OrganizerRepository repo, String name, String email, String orgName) {
        return repo.findByEmail(email).orElseGet(() -> {
            Organizer o = new Organizer();
            o.setName(name);
            o.setEmail(email);
            o.setOrganizationName(orgName);
            o.setAccessCodeHash(PasswordUtil.hash("organizer123"));
            return repo.save(o);
        });
    }
}
