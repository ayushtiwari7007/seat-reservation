package com.seatreservation.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TokenRequest {

    @NotBlank
    @Size(max = 100)
    @JsonProperty("user_id")
    @JsonAlias("userId")
    private String userId;

    @Size(max = 20)
    @JsonProperty("role")
    @JsonAlias("userRole")
    private String role;
}
