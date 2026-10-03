package com.seatreservation.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReservationStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ReservationStatus convertToEntityAttribute(String status) {
        return status == null ? null : ReservationStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
