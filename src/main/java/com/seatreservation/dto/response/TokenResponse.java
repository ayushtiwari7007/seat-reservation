package com.seatreservation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
        String token,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("user_id") String userId,
        String role) {
}
