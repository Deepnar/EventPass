package com.eventpass.eventpass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.entity.User;
import com.eventpass.eventpass.repository.BookingRepository;
import com.eventpass.eventpass.repository.CheckInRepository;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.OrganizerRepository;
import com.eventpass.eventpass.repository.PaymentRepository;
import com.eventpass.eventpass.repository.TicketRepository;
import com.eventpass.eventpass.repository.UserRepository;
import com.eventpass.eventpass.service.BookingService;
import com.eventpass.eventpass.service.EventService;
import com.eventpass.eventpass.service.OrganizerService;
import com.eventpass.eventpass.service.UserService;

@SpringBootTest
class RecommendationTest {

    @Autowired UserService userService;
    @Autowired OrganizerService organizerService;
    @Autowired EventService eventService;
    @Autowired BookingService bookingService;
    @Autowired UserRepository userRepository;
    @Autowired OrganizerRepository organizerRepository;
    @Autowired EventRepository eventRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired PaymentRepository paymentRepository;

    private User user;
    private Event concertSoon;
    private Event workshopLater;
    private Event concertLater;

    @BeforeEach
    void setUp() {
        checkInRepository.deleteAll();
        ticketRepository.deleteAll();
        bookingRepository.deleteAll();
        paymentRepository.deleteAll();
        eventRepository.deleteAll();
        organizerRepository.deleteAll();
        userRepository.deleteAll();

        user = userService.register("fan1", "secret123", "fan1@example.com");
        Organizer org = organizerService.create("Rec Org", "rec@example.com", "Rec Org Inc", "code1234");
        concertSoon = make(org.getId(), "Gig Soon", "Concert", 2, "100.00");
        workshopLater = make(org.getId(), "Craft Later", "Workshop", 5, "50.00");
        concertLater = make(org.getId(), "Gig Later", "Concert", 9, "100.00");
    }

    private Event make(Long orgId, String title, String cat, int daysOut, String price) {
        Event e = new Event();
        e.setTitle(title);
        e.setCategory(cat);
        e.setVenue("Hall");
        e.setEventDateTime(LocalDateTime.now().plusDays(daysOut));
        e.setTicketPrice(new BigDecimal(price));
        e.setTotalCapacity(50);
        e.setAvailableSeats(50);
        return eventService.create(e, orgId, "code1234");
    }

    @Test
    void bookedCategoryRanksFirst() {
        bookingService.bookTickets(user.getId(), concertLater.getId(), 1);

        List<Event> recs = eventService.getRecommended(user.getId());
        assertEquals(3, recs.size());
        // concerts first (soonest concert first), workshop last despite middle date
        assertEquals("Gig Soon", recs.get(0).getTitle());
        assertEquals("Gig Later", recs.get(1).getTitle());
        assertEquals("Craft Later", recs.get(2).getTitle());
    }

    @Test
    void newUserGetsSoonestUpcoming() {
        List<Event> recs = eventService.getRecommended(999999L);
        assertEquals(3, recs.size());
        assertTrue(recs.get(0).getEventDateTime().isBefore(recs.get(1).getEventDateTime()));
        assertTrue(recs.get(1).getEventDateTime().isBefore(recs.get(2).getEventDateTime()));
    }

    @Test
    void nullUserGetsSoonestUpcoming() {
        assertEquals(3, eventService.getRecommended(null).size());
    }
}
