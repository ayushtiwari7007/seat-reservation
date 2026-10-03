# Seat reservation: design and operational notes

## Atomic decision and deadlock avoidance

PostgreSQL is the system of record. A reservation runs in one Spring `@Transactional` transaction. It locks the idempotency key with `pg_advisory_xact_lock(hashtextextended(key, 0))`, creates/locks the `(show_id, user_id)` counter row with `SELECT ... FOR UPDATE`, and then locks all requested seat rows with `SELECT ... FOR UPDATE ORDER BY seat_label`. After verifying the complete request, it inserts the reservation, conditionally updates only `available` seat rows, increments the per-user counter, and records the idempotency key. Any conflict rolls back the transaction.

The seat row is the atomic ownership point; PostgreSQL row locks serialize contenders, and the conditional `UPDATE ... status = 'available'` is a second guard. A hot seat can therefore have at most one confirmed reservation. Multi-seat requests are all-or-nothing and lock labels in sorted order. The consistent counter-before-seats order prevents two requests for one user from bypassing the limit; deterministic seat ordering prevents seat-order deadlocks.

The database seat primary key `(show_id, seat_label)` and idempotency-key unique constraint are additional integrity barriers. Reservation status and seat ownership change in the same database transaction.

## Idempotency

The `idempotency_keys` table stores the key, user, show, SHA-256 request hash, and original reservation ID. A PostgreSQL transaction-scoped advisory lock serializes concurrent requests sharing the key before checking/inserting the record. Matching retries return the original reservation; another user/show or another seat list with the same key gets HTTP 409. Keys are not logged. A declined attempt is not persisted as an idempotent result; clients should retry a failed attempt with the same key and body.

## Holds, cancellation, and partial requests

The service confirms reservations immediately; it does not create time-boxed holds. Owners can cancel a whole reservation or one seat. Cancellation locks the reservation, user/show count row, and seats, then releases those seats and adjusts amount/count in the same transaction. A released seat is changed to `available` only when its `reservation_id` still matches the reservation being cancelled.

Multi-seat reservations are all-or-nothing: if any seat is missing or taken, the entire request returns HTTP 409 and no seats are changed.

## Consistency and availability

The service favors consistency over write availability. If PostgreSQL is unreachable, it cannot decide or commit a reservation, and it fails closed; readiness includes the database and becomes unhealthy. No write is confirmed from an in-memory fallback. Recovery requires PostgreSQL to return. In a healthy service, contention is represented by domain 409 responses rather than server errors. The show-state endpoint counts persisted seat rows and rejects a mismatch against `shows.total_seats`.

## Observability and paging

Prometheus exposes committed reservations, declined outcomes by reason, idempotent replays, and database-backed available-seat gauges by show. Actuator also exposes standard HTTP, JVM, and datasource metrics. Request logs are JSON and include a correlation ID echoed in `X-Request-Id`; no JWT, request body, or idempotency key is logged. Render operators can read the service log stream in the Render dashboard.

I would page on readiness staying down, any sustained nonzero 5xx rate, an unexpected increase in seat-taken declines that points to client retry behavior, database pool saturation/connection failures, or any difference between persisted seat counts and the show-state total. During a sale, watch request latency and PostgreSQL connections as well as reservation counters.

## AI usage

Codex assisted with reviewing the existing implementation and drafting the metrics, structured request logging, Render Blueprint, burst runner, documentation, and this write-up. The project owner chose Render and the public GitHub repository direction and remains responsible for reviewing credentials, the public deployment, and measured load-test results. No live deployment or 20,000-request result is claimed until it has actually been run.

## What I would do next

Replace the development token-minting endpoint with a trusted identity provider and admin provisioning, add automated PostgreSQL concurrency tests in CI, run the burst on a paid/staging database sized for the target load, and add alerts/dashboards for error rate, latency, pool saturation, and reconciliation.
