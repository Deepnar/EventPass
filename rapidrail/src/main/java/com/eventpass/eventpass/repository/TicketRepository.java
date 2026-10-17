package com.eventpass.eventpass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.eventpass.eventpass.entity.Ticket;

import jakarta.persistence.LockModeType;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, String> {
    Optional<Ticket> findByQrToken(String qrToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.qrToken = :qrToken")
    Optional<Ticket> findByQrTokenForUpdate(String qrToken);

    List<Ticket> findByBookingBookingId(String bookingId);
    List<Ticket> findByEventId(Long eventId);
    List<Ticket> findByEventIdAndStatus(Long eventId, String status);
    long countByBookingBookingIdAndStatus(String bookingId, String status);
}
