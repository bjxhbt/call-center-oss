\set ON_ERROR_STOP on
\if :{?admin_hash}
\else
  \echo 'Provide a BCrypt hash with -v admin_hash=... before initialization.'
  \quit 1
\endif

BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_type = 'BASE TABLE') THEN
        RAISE EXCEPTION 'Initialization requires an empty public schema';
    END IF;
END $$;
\ir 01-schema.sql
\ir 02-upgrade.sql
\ir 03-demo.sql
\ir 04-menus.sql
COMMIT;
