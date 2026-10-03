package com.seatreservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreservation.exception.BadRequestException;
import com.seatreservation.exception.ConflictException;
import com.seatreservation.exception.ResourceNotFoundException;
import com.seatreservation.dto.request.ReserveSeatRequest;
import com.seatreservation.dto.request.CancelReservationRequest;
import com.seatreservation.dto.response.ReservationResponse;
import com.seatreservation.dto.response.SeatStateResponse;
import com.seatreservation.dto.response.ShowStateResponse;
import com.seatreservation.entity.IdempotencyKey;
import com.seatreservation.entity.ReservationEntity;
import com.seatreservation.entity.ReservationStatus;
import com.seatreservation.entity.SeatEntity;
import com.seatreservation.repository.IdempotencyKeyRepository;
import com.seatreservation.repository.ReservationRepository;
import com.seatreservation.repository.ShowRepository;
import com.seatreservation.observability.ReservationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ShowRepository showRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private final ReservationMetrics metrics;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ReservationResponse reserve(UUID showId, String userId, ReserveSeatRequest request, String headerKey) {

        log.info("service.reserve.started showId={} userId={} seatCount={}",
                showId, userId, request.getSeats() == null ? 0 : request.getSeats().size());
        var show = showRepository.findById(showId).orElseThrow(() -> new ResourceNotFoundException("show not found: " + showId));

        String key = resolveIdempotencyKey(request.getIdempotencyKey(), headerKey);

        List<String> seats = normalizeSeats(request.getSeats());

        String requestHash = hash(showId + "|" + String.join(",", seats));
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, key);
                statement.execute();
            }
            return null;
        });
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(key);

        if (existing.isPresent()) {

            IdempotencyKey existingKey = existing.get();

            if (!existingKey.getUserId().equals(userId) || !existingKey.getShowId().equals(showId) || !existingKey.getRequestHash().equals(requestHash)) {
                log.warn("reservation.declined reason=idempotency_conflict showId={} userId={}", showId, userId);
                throw new ConflictException("Idempotency key already used with a different request");
            }

            ReservationEntity reservation = reservationRepository.findById(existingKey.getReservationId()).orElseThrow(() -> new ResourceNotFoundException("Reservation not found for idempotency key"));

            metrics.idempotentReplay();
            metrics.declined("idempotent_replay");
            log.info("reservation.idempotent_replay showId={} reservationId={} userId={}", showId, reservation.getId(), userId);
            return toResponse(reservation);
        }

        reservationRepository.ensureUserShowRow(showId, userId);

        int currentUserSeats = reservationRepository.lockUserShowCount(showId, userId);

        int limit = show.getPerUserLimit();

        if (currentUserSeats + seats.size() > limit) {
            metrics.declined("per_user_limit");
            log.info("reservation.declined reason=per_user_limit showId={} userId={} requestedSeats={} currentSeats={} limit={}",
                    showId, userId, seats.size(), currentUserSeats, limit);
            throw new ConflictException("per-user seat limit exceeded");
        }

        List<Object[]> locked = reservationRepository.lockSeats(showId, seats);

        if (locked.size() != seats.size()) {
            metrics.declined("seat_not_found");
            log.info("reservation.declined reason=seat_not_found showId={} userId={} requestedSeats={}",
                    showId, userId, seats.size());
            throw new ConflictException("one or more requested seats do not exist");
        }

        List<String> unavailable = locked.stream().filter(record -> !SeatEntity.AVAILABLE.equals((String) record[1])).map(record -> (String) record[0]).toList();

        if (!unavailable.isEmpty()) {
            metrics.declined("seat_taken");
            log.info("reservation.declined reason=seat_taken showId={} userId={} unavailableSeatCount={}",
                    showId, userId, unavailable.size());
            throw new ConflictException("seat(s) unavailable: " + String.join(", ", unavailable));
        }

        long amountPaise = Math.multiplyExact(show.getPricePaise(), seats.size());

        ReservationEntity reservation = new ReservationEntity();

        reservation.setShowId(showId);
        reservation.setUserId(userId);
        reservation.setSeats(writeSeats(seats));
        reservation.setAmountPaise(amountPaise);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        reservation = reservationRepository.save(reservation);

        UUID reservationId = reservation.getId();

        int confirmed = reservationRepository.confirmSeats(showId, seats, reservationId, userId);
        if (confirmed != seats.size()) {
            metrics.declined("seat_taken");
            log.info("reservation.declined reason=seat_taken showId={} userId={} requestedSeats={}",
                    showId, userId, seats.size());
            throw new ConflictException("one or more requested seats are no longer available");
        }

        reservationRepository.incrementUserShowCount(showId, userId, seats.size());

        IdempotencyKey idempotencyKey = new IdempotencyKey();

        idempotencyKey.setIdempotencyKey(key);
        idempotencyKey.setUserId(userId);
        idempotencyKey.setShowId(showId);
        idempotencyKey.setRequestHash(requestHash);
        idempotencyKey.setReservationId(reservationId);

        idempotencyKeyRepository.save(idempotencyKey);

        log.info("reservation.confirmed showId={} reservationId={} userId={} seatCount={} amountPaise={}", showId, reservationId, userId, seats.size(), amountPaise);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metrics.confirmed();
            }
        });
        return toResponse(reservation);
    }

    private static String resolveIdempotencyKey(String bodyKey, String headerKey) {

        String body = normalizeKey(bodyKey);

        String header = normalizeKey(headerKey);

        if (body != null && header != null && !body.equals(header)) {

            throw new BadRequestException("idempotency key in body and " + "Idempotency-Key header must match");
        }

        String key = header != null ? header : body;

        if (key == null) {

            throw new BadRequestException("idempotency key is required");
        }

        return key;
    }

    private static String normalizeKey(String key) {

        if (key == null) {
            return null;
        }

        String trimmed = key.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> normalizeSeats(List<String> requested) {

        if (requested == null || requested.isEmpty()) {

            throw new BadRequestException("at least one seat is required");
        }

        List<String> seats = requested.stream().map(String::trim).toList();

        if (seats.stream().anyMatch(String::isEmpty)) {

            throw new BadRequestException("seat labels must not be blank");
        }

        List<String> sorted = new ArrayList<>(seats);

        sorted.sort(String::compareTo);

        for (int i = 1; i < sorted.size(); i++) {

            if (sorted.get(i).equals(sorted.get(i - 1))) {

                throw new BadRequestException("duplicate seat label: " + sorted.get(i));
            }
        }

        return List.copyOf(sorted);
    }

    private static String hash(String value) {

        try {

            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder output = new StringBuilder(digest.length * 2);

            for (byte b : digest) {

                output.append(String.format("%02x", b));
            }

            return output.toString();

        } catch (java.security.NoSuchAlgorithmException e) {

            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId, String userId, CancelReservationRequest request) {

        log.info("service.cancel.started reservationId={} userId={} seat={}",
                reservationId, userId, request == null ? "all" : request.getSeat());
        /*
         * 1. Lock reservation row.
         */
        ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId).orElseThrow(() -> new ResourceNotFoundException("reservation not found: " + reservationId));

        /*
         * 2. Verify owner.
         */
        if (!reservation.getUserId().equals(userId)) {

            log.warn("reservation.cancel_denied reservationId={} userId={}", reservationId, userId);
            throw new ConflictException("only the reservation owner may cancel it");
        }

        /*
         * 3. Already cancelled.
         *
         * Cancellation is idempotent. A repeated cancellation returns the
         * existing cancelled reservation.
         */
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {

            return toResponse(reservation);
        }

        /*
         * 4. Ensure the user-show counter row exists.
         */
        reservationRepository.ensureUserShowRow(reservation.getShowId(), userId);

        /*
         * 5. Lock the counter so a concurrent reservation cannot change it
         * while cancellation decrements it.
         */
        reservationRepository.lockUserShowCount(reservation.getShowId(), userId);

        /*
         * 6. Lock seats belonging to this reservation.
         */
        List<Object[]> seats = reservationRepository.lockReservationSeats(reservationId);

        String requestedSeat = request == null ? null : request.getSeat();
        String seatToCancel = requestedSeat == null ? null : requestedSeat.trim();
        if (seatToCancel != null) {
            List<String> activeSeats = seats.stream().map(row -> (String) row[0]).toList();
            if (!activeSeats.contains(seatToCancel)) {
                throw new ConflictException("seat is not part of this active reservation: " + seatToCancel);
            }

            int released = reservationRepository.releaseSeat(reservationId, seatToCancel);
            if (released != 1) {
                throw new ConflictException("seat is no longer cancellable: " + seatToCancel);
            }

            reservationRepository.decrementUserShowCount(reservation.getShowId(), userId, 1);
            List<String> remainingSeats = activeSeats.stream()
                    .filter(seat -> !seat.equals(seatToCancel))
                    .toList();
            var show = showRepository.findById(reservation.getShowId())
                    .orElseThrow(() -> new ResourceNotFoundException("show not found: " + reservation.getShowId()));
            long remainingAmount = Math.subtractExact(reservation.getAmountPaise(), show.getPricePaise());
            if (remainingAmount < 0) {
                throw new IllegalStateException("reservation amount is less than the price of one seat");
            }

            reservation.setSeats(writeSeats(remainingSeats));
            reservation.setAmountPaise(remainingAmount);
            if (remainingSeats.isEmpty()) {
                reservation.setStatus(ReservationStatus.CANCELLED);
            }
        } else {
            reservationRepository.releaseSeats(reservationId);
            reservationRepository.decrementUserShowCount(reservation.getShowId(), userId, seats.size());
            reservation.setStatus(ReservationStatus.CANCELLED);
        }

        reservationRepository.save(reservation);

        log.info("reservation.cancelled showId={} reservationId={} userId={} seatCount={} seat={}",
                reservation.getShowId(), reservationId, userId,
                seatToCancel == null ? seats.size() : 1, seatToCancel);
        return toResponse(reservation);
    }

    private String writeSeats(List<String> seats) {

        try {

            return objectMapper.writeValueAsString(seats);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Unable to serialize reservation seats", e);
        }
    }

    private List<String> readSeats(String seats) {

        try {

            return objectMapper.readValue(seats, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Unable to deserialize reservation seats", e);
        }
    }

    private ReservationResponse toResponse(ReservationEntity reservation) {

        return new ReservationResponse(reservation.getId(), reservation.getShowId(), reservation.getUserId(), readSeats(reservation.getSeats()), reservation.getAmountPaise(), reservation.getStatus().name().toLowerCase());
    }

    @Transactional(readOnly = true)
    public ShowStateResponse getShowState(UUID showId) {

        log.info("service.show_state.started showId={}", showId);
        var show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("show not found: " + showId));

        List<Object[]> rows = reservationRepository.findSeatStates(showId);

        int availableSeats = 0;
        int heldSeats = 0;
        int confirmedSeats = 0;

        List<SeatStateResponse> seats = new ArrayList<>();

        for (Object[] row : rows) {

            String seatLabel = (String) row[0];
            String status = (String) row[1];

            switch (status) {
                case SeatEntity.AVAILABLE -> availableSeats++;
                case SeatEntity.HELD -> heldSeats++;
                case SeatEntity.CONFIRMED -> confirmedSeats++;
                default -> throw new IllegalStateException(
                        "Invalid seat status for show " + showId + ": " + status
                );
            }

            seats.add(new SeatStateResponse(seatLabel, status));
        }

        int totalSeats = show.getTotalSeats();
        int countedSeats = availableSeats + heldSeats + confirmedSeats;

        if (countedSeats != totalSeats || rows.size() != totalSeats) {
            throw new IllegalStateException(
                    "Seat reconciliation failed for show " + showId
                            + ": available=" + availableSeats
                            + ", held=" + heldSeats
                            + ", confirmed=" + confirmedSeats
                            + ", total=" + totalSeats
            );
        }

        return new ShowStateResponse(
                showId,
                totalSeats,
                availableSeats,
                heldSeats,
                confirmedSeats,
                seats
        );
    }
}
