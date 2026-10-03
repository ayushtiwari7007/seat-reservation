package com.seatreservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelReservationRequest {

    @NotBlank
    @Size(max = 100)
    private String seat;
}
