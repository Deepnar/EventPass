package com.eventpass.eventpass.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.Booking;
import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.repository.BookingRepository;
import com.eventpass.eventpass.repository.EventRepository;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizerService organizerService;

    @Autowired
    private BookingRepository bookingRepository;

    public List<Event> getAll() { return eventRepository.findAll(); }

    public Event getById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
    }

    public List<Event> getByCategory(String category) {
        return eventRepository.findByCategoryIgnoreCase(category);
    }

    public List<Event> getByOrganizer(Long organizerId) {
        return eventRepository.findByOrganizerId(organizerId);
    }

    public List<Event> getUpcoming() {
        return eventRepository.findByEventDateTimeAfterOrderByEventDateTimeAsc(LocalDateTime.now());
    }

    public List<Event> search(String title) {
        return eventRepository.findByTitleContainingIgnoreCase(title);
    }

    /**
     * Simple recommendations: upcoming events ranked by the user's own booking
     * history — categories they booked before come first (most-booked category
     * first), then everything else soonest-first. New users (or no user) get
     * plain soonest-upcoming. No ML, explainable in a viva.
     */
    @Transactional(readOnly = true)
    public List<Event> getRecommended(Long userId) {
        List<Event> upcoming = getUpcoming();
        if (userId == null) return upcoming;
        Set<String> preferred = new LinkedHashSet<>();
        List<Booking> history = bookingRepository.findByUserIdOrderByBookingTimeDesc(userId);
        for (Booking b : history) {
            if ("CONFIRMED".equals(b.getStatus()) && b.getEvent() != null && b.getEvent().getCategory() != null) {
                preferred.add(b.getEvent().getCategory());
            }
        }
        if (preferred.isEmpty()) return upcoming;
        List<String> rank = new ArrayList<>(preferred);
        List<Event> sorted = new ArrayList<>(upcoming);
        sorted.sort(Comparator
                .comparingInt((Event e) -> {
                    int i = rank.indexOf(e.getCategory());
                    return i < 0 ? Integer.MAX_VALUE : i;
                })
                .thenComparing(Event::getEventDateTime));
        return sorted;
    }

    @Transactional
    public Event create(Event event, Long organizerId, String organizerCode) {
        Organizer organizer = organizerService.authenticate(organizerId, organizerCode);
        validateForCreate(event);
        if (event.getAvailableSeats() == null) {
            event.setAvailableSeats(event.getTotalCapacity());
        }
        if (event.getAvailableSeats() > event.getTotalCapacity()) {
            throw new RuntimeException("Available seats cannot exceed total capacity");
        }
        event.setTicketPrice(event.getTicketPrice().setScale(2, RoundingMode.HALF_UP));
        event.setOrganizer(organizer);
        return eventRepository.save(event);
    }

    @Transactional
    public Event update(Long id, Event update, Long organizerId, String organizerCode) {
        Event existing = getById(id);
        Organizer caller = organizerService.authenticate(organizerId, organizerCode);
        if (!existing.getOrganizer().getId().equals(caller.getId())) {
            throw new RuntimeException("Not authorized: event belongs to another organizer");
        }
        if (update.getTitle() != null && !update.getTitle().isBlank()) existing.setTitle(update.getTitle().trim());
        if (update.getDescription() != null) existing.setDescription(update.getDescription());
        if (update.getCategory() != null && !update.getCategory().isBlank()) {
            validateCategory(update.getCategory());
            existing.setCategory(update.getCategory().trim());
        }
        if (update.getVenue() != null && !update.getVenue().isBlank()) existing.setVenue(update.getVenue().trim());
        if (update.getEventDateTime() != null) existing.setEventDateTime(update.getEventDateTime());
        if (update.getImageUrl() != null) existing.setImageUrl(update.getImageUrl().trim());
        if (update.getTicketPrice() != null) {
            if (update.getTicketPrice().compareTo(BigDecimal.ZERO) < 0) throw new RuntimeException("Ticket price must be >= 0");
            existing.setTicketPrice(update.getTicketPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (update.getTotalCapacity() != null) {
            if (update.getTotalCapacity() <= 0) throw new RuntimeException("Total capacity must be > 0");
            int sold = existing.getTotalCapacity() - existing.getAvailableSeats();
            if (update.getTotalCapacity() < sold) {
                throw new RuntimeException("Cannot shrink capacity below tickets already sold (" + sold + ")");
            }
            existing.setTotalCapacity(update.getTotalCapacity());
            existing.setAvailableSeats(update.getTotalCapacity() - sold);
        }
        return eventRepository.save(existing);
    }

    /**
     * Delete only when no CONFIRMED bookings reference the event.
     * Cancelled history does not block deletion.
     */
    @Transactional
    public void delete(Long id, Long organizerId, String organizerCode) {
        Event existing = getById(id);
        Organizer caller = organizerService.authenticate(organizerId, organizerCode);
        if (!existing.getOrganizer().getId().equals(caller.getId())) {
            throw new RuntimeException("Not authorized: event belongs to another organizer");
        }
        if (bookingRepository.countByEventIdAndStatus(id, "CONFIRMED") > 0) {
            throw new RuntimeException("Cannot delete event with active (CONFIRMED) bookings. Cancel them first.");
        }
        eventRepository.delete(existing);
    }

    private void validateForCreate(Event event) {
        if (event.getTitle() == null || event.getTitle().isBlank()) throw new RuntimeException("Title is required");
        if (event.getCategory() == null || event.getCategory().isBlank()) throw new RuntimeException("Category is required");
        validateCategory(event.getCategory());
        if (event.getVenue() == null || event.getVenue().isBlank()) throw new RuntimeException("Venue is required");
        if (event.getEventDateTime() == null) throw new RuntimeException("Event date/time is required");
        if (event.getTicketPrice() == null || event.getTicketPrice().compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("Ticket price must be >= 0");
        if (event.getTotalCapacity() == null || event.getTotalCapacity() <= 0)
            throw new RuntimeException("Total capacity must be > 0");
        if (event.getAvailableSeats() != null && event.getAvailableSeats() < 0)
            throw new RuntimeException("Available seats cannot be negative");
    }

    private void validateCategory(String category) {
        if (!Event.CATEGORIES.contains(category.trim())) {
            throw new RuntimeException("Category must be one of " + Event.CATEGORIES);
        }
    }
}
