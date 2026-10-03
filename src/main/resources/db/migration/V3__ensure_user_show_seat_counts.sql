-- Compatibility migration for databases created with an earlier build.
-- The current canonical table name is user_show_seat_counts.

DO $$
BEGIN
    IF to_regclass('public.user_show_seat_counts') IS NULL
       AND to_regclass('public.user_show_counts') IS NOT NULL THEN
        ALTER TABLE public.user_show_counts
            RENAME TO user_show_seat_counts;
    ELSIF to_regclass('public.user_show_seat_counts') IS NULL
          AND to_regclass('public.user_show_counts') IS NULL THEN
        CREATE TABLE public.user_show_seat_counts (
            show_id    uuid NOT NULL REFERENCES shows(id),
            user_id    text NOT NULL,
            seat_count int NOT NULL DEFAULT 0 CHECK (seat_count >= 0),
            PRIMARY KEY (show_id, user_id)
        );
    END IF;
END $$;
