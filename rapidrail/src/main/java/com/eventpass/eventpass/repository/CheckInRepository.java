package com.eventpass.eventpass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eventpass.eventpass.entity.CheckIn;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    List<CheckIn> findByEventIdOrderByCheckedInAtDesc(Long eventId);
    List<CheckIn> findByOrganizerIdOrderByCheckedInAtDesc(Long organizerId);
    Optional<CheckIn> findByTicketTicketId(String ticketId);
    boolean existsByTicketTicketId(String ticketId);
}
