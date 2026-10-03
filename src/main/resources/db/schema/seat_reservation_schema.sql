CREATE TABLE public.shows (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    price_paise bigint NOT NULL CHECK (price_paise >= 0),
    per_user_limit integer NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
    total_seats integer NOT NULL CHECK (total_seats > 0),
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE public.reservations (
    id uuid PRIMARY KEY,
    show_id uuid NOT NULL REFERENCES public.shows(id),
    user_id varchar(255) NOT NULL,
    seats text NOT NULL,
    amount_paise bigint NOT NULL CHECK (amount_paise >= 0),
    status varchar(50) NOT NULL CHECK (status IN ('confirmed', 'cancelled')),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.seats (
    show_id uuid NOT NULL REFERENCES public.shows(id),
    seat_label text NOT NULL,
    status text NOT NULL DEFAULT 'available'
        CHECK (status IN ('available', 'held', 'confirmed')),
    reservation_id uuid REFERENCES public.reservations(id),
    user_id text,
    PRIMARY KEY (show_id, seat_label),
    CHECK ((status = 'available') = (reservation_id IS NULL AND user_id IS NULL))
);

CREATE TABLE public.idempotency_keys (
    id bigserial PRIMARY KEY,
    idempotency_key varchar(255) NOT NULL,
    user_id varchar(255) NOT NULL,
    show_id uuid NOT NULL REFERENCES public.shows(id),
    request_hash varchar(255) NOT NULL,
    reservation_id uuid NOT NULL REFERENCES public.reservations(id),
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE public.user_show_seat_counts (
    show_id uuid NOT NULL REFERENCES public.shows(id),
    user_id varchar(255) NOT NULL,
    seat_count integer NOT NULL DEFAULT 0 CHECK (seat_count >= 0),
    CONSTRAINT pk_user_show_seat_counts PRIMARY KEY (show_id, user_id)
);

CREATE INDEX idx_reservations_show_user_status
    ON public.reservations(show_id, user_id, status);
CREATE INDEX idx_seats_reservation_id
    ON public.seats(reservation_id);
CREATE INDEX idx_seats_show_status
    ON public.seats(show_id, status);
