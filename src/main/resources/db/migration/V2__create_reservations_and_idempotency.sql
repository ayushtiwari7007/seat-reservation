CREATE TABLE reservations (
  id              uuid PRIMARY KEY,
  show_id         uuid NOT NULL REFERENCES shows(id),
  user_id         text NOT NULL,
  seats            text NOT NULL,
  amount_paise    bigint NOT NULL CHECK (amount_paise >= 0),
  status          text NOT NULL CHECK (status IN ('confirmed', 'cancelled')),
  created_at      timestamp NOT NULL DEFAULT now(),
  cancelled_at    timestamp NULL
);

ALTER TABLE seats
  ADD CONSTRAINT fk_seats_reservation
  FOREIGN KEY (reservation_id) REFERENCES reservations(id);

CREATE INDEX idx_reservations_show_user_status
  ON reservations(show_id, user_id, status);

CREATE TABLE idempotency_keys (
  idempotency_key text PRIMARY KEY,
  user_id         text NOT NULL,
  show_id         uuid NOT NULL REFERENCES shows(id),
  request_hash    text NOT NULL,
  reservation_id  uuid NOT NULL,
  created_at      timestamp NOT NULL DEFAULT now()
);

CREATE TABLE user_show_seat_counts (
  show_id    uuid NOT NULL REFERENCES shows(id),
  user_id    text NOT NULL,
  seat_count int NOT NULL DEFAULT 0 CHECK (seat_count >= 0),
  PRIMARY KEY (show_id, user_id)
);

CREATE INDEX idx_seats_reservation_id ON seats(reservation_id);
CREATE INDEX idx_seats_show_status ON seats(show_id, status);
