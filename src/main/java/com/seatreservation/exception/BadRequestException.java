package com.seatreservation.exception;

/** A client error that is not a bean-validation failure (e.g. duplicate seat labels). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
