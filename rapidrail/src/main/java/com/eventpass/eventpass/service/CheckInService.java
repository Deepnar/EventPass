package com.eventpass.eventpass.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventpass.eventpass.entity.CheckIn;
import com.eventpass.eventpass.entity.Event;
import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.entity.Ticket;
import com.eventpass.eventpass.repository.CheckInRepository;
import com.eventpass.eventpass.repository.EventRepository;
import com.eventpass.eventpass.repository.TicketRepository;

@Service
public class CheckInService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizerService organizerService;

    /**
     * Attempt entry. Mutates state ONLY on first valid scan:
     * marks the ticket USED and persists a CheckIn row.
     * All failure paths leave state untouched.
     */
    @Transactional
    public CheckIn checkIn(String qrToken, Long eventId, Long organizerId, String organizerCode) {
        Organizer organizer = organizerService.authenticate(organizerId, organizerCode);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CheckInException(CheckInResult.INVALID, "Event not found"));
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new CheckInException(CheckInResult.WRONG_EVENT, "Event does not belong to this organizer");
        }

        // Row lock: concurrent scans of the same QR serialize here; only one can win.
        Optional<Ticket> opt = ticketRepository.findByQrTokenForUpdate(qrToken);
        if (opt.isEmpty()) {
            throw new CheckInException(CheckInResult.INVALID, "Unknown QR token");
        }
        Ticket ticket = opt.get();

        if (!ticket.getEvent().getId().equals(eventId)) {
            throw new CheckInException(CheckInResult.WRONG_EVENT,
                    "Ticket belongs to a different event (event " + ticket.getEvent().getId() + ")");
        }
        if ("CANCELLED".equals(ticket.getStatus())) {
            throw new CheckInException(CheckInResult.CANCELLED, "Ticket was cancelled");
        }
        if ("USED".equals(ticket.getStatus()) || checkInRepository.existsByTicketTicketId(ticket.getTicketId())) {
            throw new CheckInException(CheckInResult.ALREADY_USED, "Ticket already used");
        }

        ticket.setStatus("USED");
        ticketRepository.save(ticket);
        try {
            return checkInRepository.save(new CheckIn(ticket, event, organizer));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another scan of the same ticket.
            throw new CheckInException(CheckInResult.ALREADY_USED, "Ticket already used");
        }
    }

    /**
     * Read-only preview for the UI "validate without consuming" view.
     * NEVER mutates ticket status.
     */
    @Transactional(readOnly = true)
    public CheckInResult preview(String qrToken, Long eventId) {
        Optional<Ticket> opt = ticketRepository.findByQrToken(qrToken);
        if (opt.isEmpty()) return CheckInResult.INVALID;
        Ticket ticket = opt.get();
        if (eventId != null && !ticket.getEvent().getId().equals(eventId)) return CheckInResult.WRONG_EVENT;
        if ("CANCELLED".equals(ticket.getStatus())) return CheckInResult.CANCELLED;
        if ("USED".equals(ticket.getStatus())) return CheckInResult.ALREADY_USED;
        return CheckInResult.VALID;
    }

    public List<CheckIn> getHistoryByEvent(Long eventId) {
        return checkInRepository.findByEventIdOrderByCheckedInAtDesc(eventId);
    }

    public List<CheckIn> getHistoryByOrganizer(Long organizerId) {
        return checkInRepository.findByOrganizerIdOrderByCheckedInAtDesc(organizerId);
    }

    public List<CheckIn> getAll() {
        return checkInRepository.findAll();
    }

    public Optional<CheckIn> getByTicket(String ticketId) {
        return checkInRepository.findByTicketTicketId(ticketId);
    }
}
