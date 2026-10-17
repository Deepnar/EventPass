package com.eventpass.eventpass.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.Ticket;
import com.eventpass.eventpass.repository.TicketRepository;

/**
 * Tickets are created only through booking and change state only through
 * check-in or booking cancellation. Direct create/update/delete endpoints
 * are intentionally NOT exposed; this service offers reads plus safe
 * cleanup of already-cancelled tickets.
 */
@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    public Ticket getById(String ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
    }

    public Ticket getByQrToken(String qrToken) {
        return ticketRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
    }

    public List<Ticket> getByBooking(String bookingId) {
        return ticketRepository.findByBookingBookingId(bookingId);
    }

    public List<Ticket> getByEvent(Long eventId) {
        return ticketRepository.findByEventId(eventId);
    }

    public List<Ticket> getAll() {
        return ticketRepository.findAll();
    }

    /**
     * Delete only CANCELLED tickets (e.g. cleanup after a cancelled booking).
     * VALID/USED tickets are never hard-deleted so entry history stays intact.
     */
    @Transactional
    public void delete(String ticketId) {
        Ticket t = getById(ticketId);
        if (!"CANCELLED".equals(t.getStatus())) {
            throw new RuntimeException("Only CANCELLED tickets can be deleted");
        }
        ticketRepository.delete(t);
    }
}
