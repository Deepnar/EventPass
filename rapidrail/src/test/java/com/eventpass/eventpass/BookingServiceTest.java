package com.eventpass.eventpass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.eventpass.eventpass.entity.Booking;
import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.entity.Ticket;
import com.eventpass.eventpass.entity.User;
import com.eventpass.eventpass.repository.BookingRepository;
import com.eventpass.eventpass.repository.CheckInRepository;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.OrganizerRepository;
import com.eventpass.eventpass.repository.TicketRepository;
import com.eventpass.eventpass.repository.UserRepository;
import com.eventpass.eventpass.service.BookingService;
import com.eventpass.eventpass.service.EventService;
import com.eventpass.eventpass.service.OrganizerService;
import com.eventpass.eventpass.service.UserService;

@SpringBootTest
class BookingServiceTest {

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

    private User user;
    private Event event;
    private Long organizerId;
    private static final String CODE = "testcode123";

    @BeforeEach
    void setUp() {
        checkInRepository.deleteAll();
        ticketRepository.deleteAll();
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        organizerRepository.deleteAll();
        userRepository.deleteAll();

        user = userService.register("attendee1", "secret123", "a1@example.com");
        Organizer org = organizerService.create("Org One", "org1@example.com", "Org One Inc", CODE);
        organizerId = org.getId();

        Event e = new Event();
        e.setTitle("Test Concert");
        e.setDescription("desc");
        e.setCategory("Concert");
        e.setVenue("Hall A");
        e.setEventDateTime(LocalDateTime.now().plusDays(5));
        e.setTicketPrice(new BigDecimal("100.00"));
        e.setTotalCapacity(10);
        e.setAvailableSeats(10);
        event = eventService.create(e, organizerId, CODE);
    }

    @Test
    void validBookingCreatesOneTicketPerAdmission() {
        Booking b = bookingService.bookTickets(user.getId(), event.getId(), 2);

        assertEquals("CONFIRMED", b.getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(b.getTotalAmount()));
        assertEquals(8, eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats());
        assertEquals(0, new BigDecimal("300.00").compareTo(userRepository.findById(user.getId()).orElseThrow().getBalance()));

        List<Ticket> tickets = ticketRepository.findByBookingBookingId(b.getBookingId());
        assertEquals(2, tickets.size());
        // unique unpredictable tokens
        assertEquals(2, tickets.stream().map(Ticket::getQrToken).distinct().count());
        assertTrue(tickets.stream().allMatch(t -> "VALID".equals(t.getStatus())));
    }

    @Test
    void insufficientInventoryRejected() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bookingService.bookTickets(user.getId(), event.getId(), 99));
        assertTrue(ex.getMessage().contains("Not enough seats"));
        assertEquals(10, eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats());
    }

    @Test
    void insufficientBalanceRejected() {
        // user has 500; price 100 x 6 = 600 > 500
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bookingService.bookTickets(user.getId(), event.getId(), 6));
        assertTrue(ex.getMessage().contains("Insufficient balance"));
        assertEquals(10, eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats());
    }

    @Test
    void pastEventRejected() {
        Event past = new Event();
        past.setTitle("Old Gig");
        past.setCategory("Concert");
        past.setVenue("Hall B");
        past.setEventDateTime(LocalDateTime.now().minusDays(1));
        past.setTicketPrice(new BigDecimal("50.00"));
        past.setTotalCapacity(10);
        past = eventService.create(past, organizerId, CODE);

        final Long pastId = past.getId();
        assertThrows(RuntimeException.class, () -> bookingService.bookTickets(user.getId(), pastId, 1));
    }

    @Test
    void cancellationRestoresSeatsAndRefundsExactlyOnce() {
        Booking b = bookingService.bookTickets(user.getId(), event.getId(), 2);
        Booking cancelled = bookingService.cancel(b.getBookingId());

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals(10, eventRepository.findById(event.getId()).orElseThrow().getAvailableSeats());
        assertEquals(0, new BigDecimal("500.00").compareTo(userRepository.findById(user.getId()).orElseThrow().getBalance()));
        assertTrue(ticketRepository.findByBookingBookingId(b.getBookingId())
                .stream().allMatch(t -> "CANCELLED".equals(t.getStatus())));

        // second cancel must fail (no double refund)
        assertThrows(RuntimeException.class, () -> bookingService.cancel(b.getBookingId()));
        assertEquals(0, new BigDecimal("500.00").compareTo(userRepository.findById(user.getId()).orElseThrow().getBalance()));
    }
}
