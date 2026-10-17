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
import com.eventpass.eventpass.entity.CheckIn;
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
import com.eventpass.eventpass.service.CheckInException;
import com.eventpass.eventpass.service.CheckInResult;
import com.eventpass.eventpass.service.CheckInService;
import com.eventpass.eventpass.service.EventService;
import com.eventpass.eventpass.service.OrganizerService;
import com.eventpass.eventpass.service.UserService;

@SpringBootTest
class CheckInServiceTest {

    @Autowired UserService userService;
    @Autowired OrganizerService organizerService;
    @Autowired EventService eventService;
    @Autowired BookingService bookingService;
    @Autowired CheckInService checkInService;
    @Autowired UserRepository userRepository;
    @Autowired OrganizerRepository organizerRepository;
    @Autowired EventRepository eventRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired CheckInRepository checkInRepository;

    private User user;
    private Event eventA;
    private Event eventB;
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

        user = userService.register("attendee2", "secret123", "a2@example.com");
        Organizer org = organizerService.create("Org Two", "org2@example.com", "Org Two Inc", CODE);
        organizerId = org.getId();

        eventA = makeEvent("Concert A", "Concert", 10, new BigDecimal("100.00"));
        eventB = makeEvent("Workshop B", "Workshop", 10, new BigDecimal("50.00"));
    }

    private Event makeEvent(String title, String category, int capacity, BigDecimal price) {
        Event e = new Event();
        e.setTitle(title);
        e.setCategory(category);
        e.setVenue("Venue " + title);
        e.setEventDateTime(LocalDateTime.now().plusDays(5));
        e.setTicketPrice(price);
        e.setTotalCapacity(capacity);
        e.setAvailableSeats(capacity);
        return eventService.create(e, organizerId, CODE);
    }

    @Test
    void validCheckInCreatesPersistentRecord() {
        Booking b = bookingService.bookTickets(user.getId(), eventA.getId(), 2);
        List<Ticket> tickets = ticketRepository.findByBookingBookingId(b.getBookingId());

        CheckIn ci = checkInService.checkIn(tickets.get(0).getQrToken(), eventA.getId(), organizerId, CODE);
        assertEquals("USED", ticketRepository.findById(tickets.get(0).getTicketId()).orElseThrow().getStatus());
        assertEquals(1, checkInRepository.findByEventIdOrderByCheckedInAtDesc(eventA.getId()).size());
        assertEquals(ci.getId(), checkInRepository.findByTicketTicketId(tickets.get(0).getTicketId()).orElseThrow().getId());

        // second ticket on the same booking checks in independently
        checkInService.checkIn(tickets.get(1).getQrToken(), eventA.getId(), organizerId, CODE);
        assertEquals(2, checkInRepository.findByEventIdOrderByCheckedInAtDesc(eventA.getId()).size());
    }

    @Test
    void duplicateCheckInRejected() {
        Booking b = bookingService.bookTickets(user.getId(), eventA.getId(), 1);
        String token = ticketRepository.findByBookingBookingId(b.getBookingId()).get(0).getQrToken();

        checkInService.checkIn(token, eventA.getId(), organizerId, CODE);
        CheckInException ex = assertThrows(CheckInException.class,
                () -> checkInService.checkIn(token, eventA.getId(), organizerId, CODE));
        assertEquals(CheckInResult.ALREADY_USED, ex.getResult());
        assertEquals(1, checkInRepository.findByEventIdOrderByCheckedInAtDesc(eventA.getId()).size());
    }

    @Test
    void invalidTokenRejected() {
        CheckInException ex = assertThrows(CheckInException.class,
                () -> checkInService.checkIn("no-such-token", eventA.getId(), organizerId, CODE));
        assertEquals(CheckInResult.INVALID, ex.getResult());
    }

    @Test
    void wrongEventRejected() {
        Booking b = bookingService.bookTickets(user.getId(), eventA.getId(), 1);
        String token = ticketRepository.findByBookingBookingId(b.getBookingId()).get(0).getQrToken();

        CheckInException ex = assertThrows(CheckInException.class,
                () -> checkInService.checkIn(token, eventB.getId(), organizerId, CODE));
        assertEquals(CheckInResult.WRONG_EVENT, ex.getResult());
        // ticket untouched
        assertEquals("VALID", ticketRepository.findByQrToken(token).orElseThrow().getStatus());
        assertTrue(checkInRepository.findByEventIdOrderByCheckedInAtDesc(eventB.getId()).isEmpty());
    }

    @Test
    void cancelledTicketRejectedAndBlocksBookingCancelAfterUse() {
        Booking b = bookingService.bookTickets(user.getId(), eventA.getId(), 1);
        String token = ticketRepository.findByBookingBookingId(b.getBookingId()).get(0).getQrToken();

        bookingService.cancel(b.getBookingId());
        CheckInException ex = assertThrows(CheckInException.class,
                () -> checkInService.checkIn(token, eventA.getId(), organizerId, CODE));
        assertEquals(CheckInResult.CANCELLED, ex.getResult());
    }

    @Test
    void cancelAfterCheckInBlocked() {
        Booking b = bookingService.bookTickets(user.getId(), eventA.getId(), 1);
        String token = ticketRepository.findByBookingBookingId(b.getBookingId()).get(0).getQrToken();
        checkInService.checkIn(token, eventA.getId(), organizerId, CODE);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> bookingService.cancel(b.getBookingId()));
        assertTrue(ex.getMessage().contains("already checked in"));
    }

    @Test
    void previewDoesNotMutate() {
        Booking b = bookingService.bookTickets(user.getId(), eventA.getId(), 1);
        String token = ticketRepository.findByBookingBookingId(b.getBookingId()).get(0).getQrToken();

        assertEquals(CheckInResult.VALID, checkInService.preview(token, eventA.getId()));
        assertEquals("VALID", ticketRepository.findByQrToken(token).orElseThrow().getStatus());
        assertTrue(checkInRepository.findByEventIdOrderByCheckedInAtDesc(eventA.getId()).isEmpty());
        assertEquals(CheckInResult.WRONG_EVENT, checkInService.preview(token, eventB.getId()));
        assertEquals(CheckInResult.INVALID, checkInService.preview("bogus", eventA.getId()));
    }
}
