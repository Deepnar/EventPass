package com.eventpass.eventpass.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.Booking;
import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Ticket;
import com.eventpass.eventpass.entity.User;
import com.eventpass.eventpass.repository.BookingRepository;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.TicketRepository;
import com.eventpass.eventpass.repository.UserRepository;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Transactional
    public Booking bookTickets(Long userId, Long eventId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("Quantity must be > 0");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Pessimistic row lock prevents overselling under concurrent requests.
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getEventDateTime() == null || event.getEventDateTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot book a past event");
        }
        if (event.getAvailableSeats() < quantity) {
            throw new RuntimeException("Not enough seats available. Only " + event.getAvailableSeats() + " left");
        }

        BigDecimal totalAmount = event.getTicketPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        if (user.getBalance().compareTo(totalAmount) < 0) {
            throw new RuntimeException("Insufficient balance. Need Rs." + totalAmount
                    + " but have Rs." + user.getBalance());
        }

        user.setBalance(user.getBalance().subtract(totalAmount).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);

        event.setAvailableSeats(event.getAvailableSeats() - quantity);
        eventRepository.save(event);

        Booking booking = new Booking(UUID.randomUUID().toString(), user, event, quantity, totalAmount);
        booking = bookingRepository.save(booking);

        // One ticket per admission, each with an unpredictable QR token.
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            tickets.add(new Ticket(UUID.randomUUID().toString(), booking, event, UUID.randomUUID().toString()));
        }
        ticketRepository.saveAll(tickets);

        return booking;
    }

    public Booking getById(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
    }

    public List<Booking> getByUser(Long userId) {
        return bookingRepository.findByUserIdOrderByBookingTimeDesc(userId);
    }

    public List<Booking> getByEvent(Long eventId) {
        return bookingRepository.findByEventId(eventId);
    }

    public List<Booking> getAll() {
        return bookingRepository.findAll();
    }

    /**
     * Cancel: restores seats and refunds exactly once. Blocked when any ticket
     * on the booking was already checked in (USED), since entry already happened.
     */
    @Transactional
    public Booking cancel(String bookingId) {
        Booking booking = getById(bookingId);
        if ("CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking already cancelled");
        }
        long used = ticketRepository.countByBookingBookingIdAndStatus(bookingId, "USED");
        if (used > 0) {
            throw new RuntimeException("Cannot cancel: " + used + " ticket(s) already checked in");
        }

        Event event = eventRepository.findByIdForUpdate(booking.getEvent().getId())
                .orElseThrow(() -> new RuntimeException("Event not found"));
        event.setAvailableSeats(event.getAvailableSeats() + booking.getQuantity());
        eventRepository.save(event);

        User user = booking.getUser();
        user.setBalance(user.getBalance().add(booking.getTotalAmount()).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);

        // Mark sibling tickets cancelled so their QR codes stop working.
        List<Ticket> tickets = ticketRepository.findByBookingBookingId(bookingId);
        for (Ticket t : tickets) {
            if ("VALID".equals(t.getStatus())) t.setStatus("CANCELLED");
        }
        ticketRepository.saveAll(tickets);

        booking.setStatus("CANCELLED");
        return bookingRepository.save(booking);
    }

    /**
     * Hard delete only for CANCELLED bookings (safe cleanup). Active bookings
     * must be cancelled first so inventory/refund rules always run.
     */
    @Transactional
    public void delete(String bookingId) {
        Booking booking = getById(bookingId);
        if (!"CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Only CANCELLED bookings can be deleted. Cancel the booking first.");
        }
        ticketRepository.deleteAll(ticketRepository.findByBookingBookingId(bookingId));
        bookingRepository.delete(booking);
    }
}
