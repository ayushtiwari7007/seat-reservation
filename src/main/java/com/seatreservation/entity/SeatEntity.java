package com.seatreservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

@Entity
@Table(name = "seats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatEntity {

    public static final String AVAILABLE = "available";
    public static final String HELD = "held";
    public static final String CONFIRMED = "confirmed";

    @EmbeddedId
    private SeatId id;

    @Column(nullable = false)
    private String status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "user_id")
    private String userId;
}
