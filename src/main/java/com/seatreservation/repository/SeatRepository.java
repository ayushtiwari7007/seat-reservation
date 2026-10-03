package com.seatreservation.repository;

import com.seatreservation.entity.SeatEntity;
import com.seatreservation.entity.SeatId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<SeatEntity, SeatId> {
}
