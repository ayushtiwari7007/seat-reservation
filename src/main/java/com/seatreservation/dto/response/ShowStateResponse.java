package com.seatreservation.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ShowStateResponse(
        @JsonProperty("show_id")
        @JsonAlias("showId")
        UUID showId,

        @JsonProperty("total_seats")
        @JsonAlias("totalSeats")
        int totalSeats,

        @JsonProperty("available_seats")
        @JsonAlias("availableSeats")
        int availableSeats,

        @JsonProperty("held_seats")
        @JsonAlias("heldSeats")
        int heldSeats,

        @JsonProperty("confirmed_seats")
        @JsonAlias("confirmedSeats")
        int confirmedSeats,

        List<SeatStateResponse> seats
) {
}
