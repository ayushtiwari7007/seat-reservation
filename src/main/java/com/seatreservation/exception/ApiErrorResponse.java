package com.seatreservation.exception;

import java.util.List;

/** Shared error response shape used by controllers and the authentication filter. */
public record ApiErrorResponse(int statusCode, String errorMessage, List<String> details) {
    public ApiErrorResponse(int statusCode, String errorMessage) {
        this(statusCode, errorMessage, List.of());
    }
}
