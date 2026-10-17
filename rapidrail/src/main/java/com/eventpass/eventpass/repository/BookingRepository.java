package com.eventpass.eventpass.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eventpass.eventpass.entity.Booking;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findByUserIdOrderByBookingTimeDesc(Long userId);
    List<Booking> findByEventId(Long eventId);
    List<Booking> findByEventIdAndStatus(Long eventId, String status);
    long countByEventIdAndStatus(Long eventId, String status);
}
