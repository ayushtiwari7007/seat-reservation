package com.seatreservation.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ReservationMetrics {
    private final MeterRegistry registry;
    private final JdbcTemplate jdbcTemplate;
    // Micrometer gauges keep only a weak reference to their state object. Retain these
    // UUIDs so a registered show's gauge remains readable after garbage collection.
    private final ConcurrentMap<UUID, UUID> showGaugeStates = new ConcurrentHashMap<>();

    public ReservationMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.registry = registry;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void confirmed() {
        registry.counter("reservations.confirmed").increment();
    }

    public void declined(String reason) {
        registry.counter("reservations.declined", "reason", reason).increment();
    }

    public void idempotentReplay() {
        registry.counter("reservations.idempotent.replay").increment();
    }

    public void registerShow(UUID showId) {
        UUID gaugeState = showGaugeStates.computeIfAbsent(showId, id -> id);
        registry.gauge("seats.available", io.micrometer.core.instrument.Tags.of("show_id", showId.toString()),
                gaugeState, id -> {
                    try {
                        Integer count = jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM seats WHERE show_id = ? AND status = 'available'",
                                Integer.class, id);
                        return count == null ? 0 : count;
                    } catch (RuntimeException ex) {
                        return Double.NaN;
                    }
                });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerExistingShows() {
        jdbcTemplate.query("SELECT id FROM shows", (rs, row) -> rs.getObject(1, UUID.class))
                .forEach(this::registerShow);
    }
}
