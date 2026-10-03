-- Aligns the legacy Flyway-created schema with the current PostgreSQL layout.
-- Also supports a pre-existing manually-created schema when Flyway baselines it at V3.

ALTER TABLE public.reservations
    ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.idempotency_keys
    ADD COLUMN IF NOT EXISTS id bigserial;

DO $$
DECLARE
    current_pk text;
    id_is_pk boolean;
BEGIN
    SELECT c.conname,
           bool_or(a.attname = 'id') AND count(*) = 1
      INTO current_pk, id_is_pk
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
      LEFT JOIN unnest(c.conkey) WITH ORDINALITY AS key(attnum, ord) ON true
      LEFT JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = key.attnum
     WHERE n.nspname = 'public'
       AND t.relname = 'idempotency_keys'
       AND c.contype = 'p'
     GROUP BY c.conname;

    IF current_pk IS NOT NULL AND NOT COALESCE(id_is_pk, false) THEN
        EXECUTE format('ALTER TABLE public.idempotency_keys DROP CONSTRAINT %I', current_pk);
        current_pk := NULL;
    END IF;

    IF current_pk IS NULL THEN
        ALTER TABLE public.idempotency_keys
            ADD CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        JOIN unnest(c.conkey) AS key(attnum) ON true
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = key.attnum
        WHERE n.nspname = 'public'
          AND t.relname = 'idempotency_keys'
          AND c.contype IN ('p', 'u')
        GROUP BY c.oid
        HAVING array_agg(a.attname ORDER BY a.attname) = ARRAY['idempotency_key']::name[]
    ) THEN
        ALTER TABLE public.idempotency_keys
            ADD CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key);
    END IF;
END $$;

ALTER TABLE public.idempotency_keys
    ALTER COLUMN idempotency_key TYPE varchar(255) USING idempotency_key::varchar(255),
    ALTER COLUMN user_id TYPE varchar(255) USING user_id::varchar(255),
    ALTER COLUMN request_hash TYPE varchar(255) USING request_hash::varchar(255);

ALTER TABLE public.reservations
    ALTER COLUMN user_id TYPE varchar(255) USING user_id::varchar(255),
    ALTER COLUMN status TYPE varchar(50) USING status::varchar(50);

ALTER TABLE public.user_show_seat_counts
    ALTER COLUMN user_id TYPE varchar(255) USING user_id::varchar(255);
