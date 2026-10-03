package com.seatreservation.repository;

import com.seatreservation.entity.ReservationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository
        extends JpaRepository<ReservationEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM ReservationEntity r
            WHERE r.id = :reservationId
            """)
    Optional<ReservationEntity> findByIdForUpdate(
            @Param("reservationId") UUID reservationId
    );

    // Lock requested seats
    @Query(value = """
        SELECT seat_label, status
        FROM seats
        WHERE show_id = :showId
          AND seat_label IN (:seats)
        ORDER BY seat_label
        FOR UPDATE
        """, nativeQuery = true)
    List<Object[]> lockSeats(
            @Param("showId") UUID showId,
            @Param("seats") List<String> seats
    );

    // Read the current state of every seat for a show
    @Query(value = """
        SELECT seat_label, status
        FROM seats
        WHERE show_id = :showId
        ORDER BY seat_label
        """, nativeQuery = true)
    List<Object[]> findSeatStates(
            @Param("showId") UUID showId
    );

    // Confirm seats
    @Modifying
    @Query(value = """
            UPDATE seats
            SET status = 'confirmed',
                reservation_id = :reservationId,
                user_id = :userId
            WHERE show_id = :showId
              AND seat_label IN (:seats)
              AND status = 'available'
            """, nativeQuery = true)
    int confirmSeats(
            @Param("showId") UUID showId,
            @Param("seats") List<String> seats,
            @Param("reservationId") UUID reservationId,
            @Param("userId") String userId
    );

    // Create user-show count row if absent (PostgreSQL)
    @Modifying
    @Query(value = """
            INSERT INTO user_show_seat_counts
                (show_id, user_id, seat_count)
            VALUES
                (:showId, :userId, 0)
            ON CONFLICT (show_id, user_id)
            DO NOTHING
            """, nativeQuery = true)
    void ensureUserShowRow(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    // Lock user-show count row
    @Query(value = """
            SELECT seat_count
            FROM user_show_seat_counts
            WHERE show_id = :showId
              AND user_id = :userId
            FOR UPDATE
            """, nativeQuery = true)
    Integer lockUserShowCount(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    // Increment user's seat count
    @Modifying
    @Query(value = """
            UPDATE user_show_seat_counts
            SET seat_count = seat_count + :count
            WHERE show_id = :showId
              AND user_id = :userId
            """, nativeQuery = true)
    void incrementUserShowCount(
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("count") int count
    );

    // Decrement user's seat count
    @Modifying
    @Query(value = """
            UPDATE user_show_seat_counts
            SET seat_count = GREATEST(0, seat_count - :count)
            WHERE show_id = :showId
              AND user_id = :userId
            """, nativeQuery = true)
    void decrementUserShowCount(
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("count") int count
    );

    // Lock seats associated with a reservation
    @Query(value = """
        SELECT seat_label, status
        FROM seats
        WHERE reservation_id = :reservationId
        ORDER BY seat_label
        FOR UPDATE
        """, nativeQuery = true)
    List<Object[]> lockReservationSeats(
            @Param("reservationId") UUID reservationId
    );

    // Release reservation seats
    @Modifying
    @Query(value = """
            UPDATE seats
            SET status = 'available',
                reservation_id = NULL,
                user_id = NULL
            WHERE reservation_id = :reservationId
            """, nativeQuery = true)
    void releaseSeats(
            @Param("reservationId") UUID reservationId
    );

    @Modifying
    @Query(value = """
            UPDATE seats
            SET status = 'available', reservation_id = NULL, user_id = NULL
            WHERE reservation_id = :reservationId
              AND seat_label = :seatLabel
              AND status = 'confirmed'
            """, nativeQuery = true)
    int releaseSeat(
            @Param("reservationId") UUID reservationId,
            @Param("seatLabel") String seatLabel
    );
}
