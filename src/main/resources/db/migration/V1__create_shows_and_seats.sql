-- V1: tables needed for POST /shows
-- Later migrations add: reservations, user_show_seat_counts, idempotency_keys, and the indexes
-- that the reserve/cancel/GET paths need.

CREATE TABLE shows (
  id              uuid        PRIMARY KEY,
  name            text        NOT NULL,
  price_paise     bigint      NOT NULL CHECK (price_paise >= 0),
  per_user_limit  int         NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
  total_seats     int         NOT NULL CHECK (total_seats > 0),
  created_at      timestamp NOT NULL DEFAULT now()
);

CREATE TABLE seats (
  show_id         uuid NOT NULL REFERENCES shows (id),
  seat_label      text NOT NULL,
  status          text NOT NULL DEFAULT 'available'
                  CHECK (status IN ('available', 'held', 'confirmed')),
  reservation_id  uuid NULL,   -- FK to reservations is added when that table exists
  user_id         text NULL,
  PRIMARY KEY (show_id, seat_label),
  -- a seat is either free (no owner) or owned (has an owner): never half-and-half
  CHECK ((status = 'available') = (reservation_id IS NULL AND user_id IS NULL))
);
