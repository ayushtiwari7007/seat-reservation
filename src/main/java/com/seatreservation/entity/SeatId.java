package com.seatreservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatId implements Serializable {

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "seat_label", nullable = false)
    private String seatLabel;
}
