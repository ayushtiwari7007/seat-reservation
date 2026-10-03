package com.seatreservation.controller;

import com.seatreservation.auth.JwtAuthenticationFilter;
import com.seatreservation.dto.request.CancelReservationRequest;
import com.seatreservation.dto.request.ReserveSeatRequest;
import com.seatreservation.dto.response.ReservationResponse;
import com.seatreservation.dto.response.ShowStateResponse;
import com.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/shows/{showId}")
    public ResponseEntity<ShowStateResponse> getShowState(
            @PathVariable UUID showId) {

        log.info("controller.show_state.request showId={}", showId);
        return ResponseEntity.ok(reservationService.getShowState(showId));
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) String userId,
            @Valid @RequestBody ReserveSeatRequest request) {

        log.info("controller.reserve.request showId={} userId={} seatCount={}",
                showId, userId, request.getSeats() == null ? 0 : request.getSeats().size());
        ReservationResponse response = reservationService.reserve(showId, userId, request, headerKey);
        return ResponseEntity.created(URI.create("/reservations/" + response.reservationId())).body(response);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID reservationId,
            @RequestAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) String userId,
            @Valid @RequestBody(required = false) CancelReservationRequest request) {
        log.info("controller.cancel.request reservationId={} userId={} seat={}",
                reservationId, userId, request == null ? "all" : request.getSeat());
        return ResponseEntity.ok(reservationService.cancel(reservationId, userId, request));
    }
}
