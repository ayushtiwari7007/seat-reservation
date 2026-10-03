package com.seatreservation.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReserveSeatRequest {

    @NotEmpty
    @Size(max = 100)
    private List<@Size(min = 1, max = 100) String> seats;

    /** Optional when Idempotency-Key header is supplied. */
    @Size(max = 200)
    @JsonProperty("idempotency_key")
    @JsonAlias("idempotencyKey")
    private String idempotencyKey;
}
