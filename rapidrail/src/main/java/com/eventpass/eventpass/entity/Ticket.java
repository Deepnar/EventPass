package com.eventpass.eventpass.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private String ticketId; // UUID

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Unpredictable per-ticket token encoded in the QR code. Unique + indexed.
    @Column(nullable = false, unique = true, name = "qr_token")
    private String qrToken;

    @Column(nullable = false)
    private String status; // VALID, USED, CANCELLED

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    public Ticket() {}

    public Ticket(String ticketId, Booking booking, Event event, String qrToken) {
        this.ticketId = ticketId;
        this.booking = booking;
        this.event = event;
        this.qrToken = qrToken;
        this.status = "VALID";
        this.createdAt = LocalDateTime.now();
    }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
