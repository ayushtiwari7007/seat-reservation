package com.seatreservation.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.List;

@Data
public class CreateShowRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<String> seats;

    @NotNull
    @PositiveOrZero
    @JsonProperty("price_paise")
    @JsonAlias("pricePaise")
    private Long pricePaise;

    @Positive
    @JsonProperty("per_user_limit")
    @JsonAlias("perUserLimit")
    private Integer perUserLimit;
}
