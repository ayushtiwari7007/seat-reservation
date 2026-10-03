# Seat Reservation Service

## Implemented

### 1. Create show
`POST /shows` (JWT role `ADMIN`)

### 2. Reserve seats
`POST /shows/{id}/reserve` (authenticated user)

Authentication uses a signed JWT. The JWT subject (`sub`) is the authenticated `user_id`; the reservation JSON body never accepts `userId`.

For local testing, first request a JWT:

```bash
curl -X POST localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"user_id":"user-123","role":"USER"}'
```

Use the returned token on reservation/cancellation requests:

```bash
-H "Authorization: Bearer <JWT>"
```

For `POST /shows`, issue an admin JWT by requesting `role=ADMIN` in the token request:

```bash
curl -X POST localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"user_id":"admin-123","role":"ADMIN"}'
```

There is no static admin token or `AdminAuthInterceptor`; all protected endpoints use the same JWT validation filter.

The `/auth/token` endpoint is a minimal token-issuing adapter because this project does not currently contain a user/password store. In a production system, the user would first authenticate through an identity provider or credentials service, and that trusted identity would be used as the JWT subject.

Reservation semantics:

- **All-or-nothing** for multi-seat requests. If any requested seat is unavailable or does not exist, the complete request is rejected with `409`; no seat is reserved.
- **Per-user limit** is enforced from `shows.per_user_limit` and defaults to 4.
- **Idempotency** is required. It can be supplied as `Idempotency-Key` or `idempotency_key` in the JSON body. If both are present they must match.
- A repeated key with the same user/show/seats returns the original reservation. Requests sharing a key serialize with a PostgreSQL transaction-scoped advisory lock, including concurrent retries.
- A repeated key with a different user, show, or seat set returns `409`.
- Seat rows are locked with `SELECT ... FOR UPDATE` in deterministic label order, so a concurrent race for one seat has exactly one winner.
- A separate `user_show_seat_counts` row is locked before seat rows, preventing two concurrent requests from bypassing the per-user limit.

Example:

```bash
curl -i -X POST localhost:8080/shows/<SHOW_ID>/reserve \
  -H "Authorization: Bearer <JWT>" \
  -H "Idempotency-Key: reserve-001" \
  -H "Content-Type: application/json" \
  -d '{"seats":["A12"]}'
```

Success (`201`):

```json
{
  "reservation_id": "...",
  "show_id": "...",
  "user_id": "user-123",
  "seats": ["A12"],
  "amount_paise": 25000,
  "status": "confirmed"
}
```

### 3. Cancel a reservation

This implementation chooses **explicit cancellation** rather than time-boxed holds:

`POST /reservations/{id}/cancel`

Only the authenticated owner can cancel. Send no body to cancel the whole reservation, or provide one `seat` to cancel just that seat. Single-seat cancellation releases that seat, removes it from the reservation's active seat list, adjusts its amount, and decrements the user's per-show count atomically. The reservation becomes `cancelled` when its final seat is cancelled.

```bash
curl -i -X POST localhost:8080/reservations/<RESERVATION_ID>/cancel \
  -H "Authorization: Bearer <JWT>"
```

To cancel only one seat:

```bash
curl -i -X POST localhost:8080/reservations/<RESERVATION_ID>/cancel \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"seat":"A12"}'
```

A released seat is returned to `available` in the same database transaction. The cancellation locks the user/show row, reservation row, and reservation's seat rows in a consistent order, preventing a concurrent reserve/cancel operation from resurrecting a seat incorrectly.

## Run locally

```bash
docker compose up -d db
mvn spring-boot:run
```

Flyway applies all database migrations on startup.

The default PostgreSQL database is `seat_reservation_db`. If your existing data is in a database named `seats`, changing `DB_URL` to the new database name does not move that data; keep using the old URL or migrate the database contents separately.

Config via env: `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE`, `PORT`.

## Deploy with Docker Compose

```bash
docker compose up --build
```

Set `JWT_SECRET` to a random value of at least 32 characters and set `DB_PASSWORD` before deploying beyond local development. Compose defaults are for local use. `/auth/token` is a development token issuer; do not expose it as production authentication.

## Deploy to Render

`render.yaml` defines a Docker web service and a PostgreSQL database. Connect this public repository as a Render Blueprint and apply it. Render builds the included `Dockerfile`, injects database connection settings and a generated JWT secret, runs Flyway at startup, and checks `/actuator/health/readiness` before routing traffic.

The free Render PostgreSQL instance is suitable for a demonstration, but Render documents that it expires after 30 days. Upgrade it before relying on persistent data. Free web instances can spin down when idle, so the first request after inactivity can take longer. See Render's [free instance limitations](https://render.com/docs/free) and [health check behavior](https://render.com/docs/health-checks).

`/auth/token` remains a development-only identity adapter: anyone who can reach it can mint a token for an arbitrary user ID and role. Do not use this deployment as a real ticketing system until this endpoint is replaced with trusted authentication/authorization.

## Health, metrics, and logs

- Liveness: `GET /actuator/health/liveness` (process state only).
- Readiness: `GET /actuator/health/readiness` (includes the database; reports down if PostgreSQL cannot be reached).
- Prometheus scrape: `GET /actuator/prometheus`.
- The metrics endpoint and health probes are public for deployment monitoring. Health details remain hidden.
- `reservations_confirmed_total` counts committed reservations.
- `reservations_declined_total{reason="seat_taken|per_user_limit|seat_not_found|idempotent_replay"}` counts domain declines/replays; idempotent replay also has its own `reservations_idempotent_replay_total` counter.
- `seats_available{show_id="..."}` is queried from the database when scraped and includes shows restored after application restart.
- Other Spring/Micrometer HTTP, JVM, and datasource metrics are exposed by Actuator.
- Console output is Logstash JSON. Each request receives an `X-Request-Id` (or a validated UUID supplied with that header); the same ID is returned in the response and included in request logs. In Render, open the service's **Logs** page for live logs. The platform does not provide an unauthenticated public log URL by default.

## Run the burst against a fresh deployment

Use a staging service because the script creates a new show and leaves its reservations in the database. Python 3 is the only local prerequisite:

```bash
./burst.sh https://YOUR-SERVICE.onrender.com
```

The default run sends 500 distinct users to one hot seat, then checks same-key replay/body mismatch, races ten requests from one user against a four-seat limit, and fetches the final show state. It prints outcome distributions, 5xx count, and the reconciliation result; it exits nonzero if a check fails.

Tune the hot-seat stampede with environment variables:

```bash
BURST_USERS=20000 BURST_WORKERS=500 HTTP_TIMEOUT=180 ./burst.sh https://YOUR-SERVICE.onrender.com
```

This sends 20,000 attempts using up to 500 client workers. The free Render plan is not sized to guarantee a 20,000-request-per-second load test; use the script result and platform metrics/logs to report the actual run, rather than treating a configured request count as measured throughput.

See [WRITEUP.md](WRITEUP.md) for the atomicity, idempotency, failure model, and operational notes.

## Health and metrics

- Health: `GET /actuator/health`
- Application metrics: `GET /actuator/metrics` (append a metric name to inspect it)
- Prometheus scrape: `GET /actuator/prometheus`

Swagger UI: `GET /swagger-ui.html` (OpenAPI JSON: `GET /v3/api-docs`). Use **Authorize** in Swagger UI to set a bearer JWT for protected endpoints.

Only these Actuator endpoints are exposed over HTTP, and health details are hidden.

`src/main/resources/db/schema/seat_reservation_schema.sql` is the complete create-only schema snapshot for a new database. The versioned files under `db/migration` remain incremental Flyway migrations for existing databases; do not delete or rewrite migrations that have already been applied.

All API errors use the same JSON shape: `statusCode`, `errorMessage`, and `details` (empty for errors without field-level details). For example, using a USER token to create a show returns HTTP 403 with `{"statusCode":403,"errorMessage":"admin role is required to create a show","details":[]}`.

Reservation logs use parameterized SLF4J messages with event types and relevant identifiers/counts. Request bodies, JWTs, and idempotency keys are not logged.

### JSON naming

The global Jackson `SNAKE_CASE` naming strategy is intentionally not used. Request and response DTOs explicitly define their JSON names with `@JsonProperty` and accepted camelCase aliases with `@JsonAlias` where needed. This keeps JSON naming local to the API contract instead of changing serialization globally.
