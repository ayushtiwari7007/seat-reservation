package com.seatreservation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record CreateShowResponse(
        UUID id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats,
        Counts counts,
        List<SeatView> seats) {

    public record Counts(
            int available,
            int held,
            int confirmed) {}

    public record SeatView(String seat, String status) {}

    /** A freshly created show: every seat is available by definition. */
    public static CreateShowResponse created(UUID id, String name, long pricePaise, int perUserLimit, List<String> labels) {
        List<SeatView> seats = labels.stream().map(l -> new SeatView(l, "available")).toList();
        return new CreateShowResponse(id, name, pricePaise, perUserLimit, labels.size(),
                new Counts(labels.size(), 0, 0), seats);
    }
}
