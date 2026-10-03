package com.seatreservation.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SeatStateResponse(
        @JsonProperty("seat_id")
        @JsonAlias("seatId")
        String seatId,

        String status
) {
}
