-- grading.hrlab.uz — local-dev role bootstrap.
-- Optional pre-seed of the three runtime roles for developers who run
-- `psql` directly against the dockerised Postgres without booting Spring.
-- Liquibase changelog 005-db-role-grants.yaml is the source of truth for
-- grants; this script only creates the role principals + LOGIN passwords
-- so direct psql sessions can authenticate.
--
-- IDEMPOTENT: re-running is a no-op (guarded by pg_roles lookup).
-- LOCAL-DEV-ONLY: passwords here are well-known; never reuse in any
-- deployed environment (devops-sre rotates real passwords via Vault).

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_migrator') THEN
    CREATE ROLE grading_migrator LOGIN PASSWORD 'grading_migrator_pwd';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_runtime') THEN
    CREATE ROLE grading_runtime LOGIN PASSWORD 'grading_runtime_pwd';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_audit_reader') THEN
    CREATE ROLE grading_audit_reader LOGIN PASSWORD 'grading_audit_reader_pwd';
  END IF;
END
$$;
