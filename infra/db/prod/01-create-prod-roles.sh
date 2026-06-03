#!/bin/sh
# =============================================================================
# grading.hrlab.uz — PRODUCTION DB role bootstrap (first-init only).
# =============================================================================
# The postgres official image runs *.sh and *.sql files in
# /docker-entrypoint-initdb.d/ EXACTLY ONCE, on the first initialisation of an
# EMPTY data volume (grading-pg-data-prod). This script attaches LOGIN +
# passwords to the three F-04 roles so:
#
#   grading_migrator      -> Liquibase (DDL). Used by the grading-migrator svc.
#   grading_runtime       -> the API at runtime (DML; INSERT+SELECT only on
#                            audit tables — UPDATE/DELETE revoked by changelog 005).
#   grading_audit_reader  -> audit-query path (SELECT on audit tables).
#
# Why a shell script, not the `test-roles` Liquibase context:
#   `test-roles` ships well-known DEV passwords and MUST NEVER run in prod.
#   Here, passwords are read from the container ENVIRONMENT (set from .env.prod),
#   so NO secret is in git. Changelog 005 (run later by Liquibase) creates the
#   roles if missing and applies all the GRANTs/REVOKEs; this script only makes
#   sure they exist WITH LOGIN + the right passwords before the first migration.
#
# Idempotent: guarded by pg_roles lookups. Safe to leave mounted.
# For an EXTERNAL Postgres, run the equivalent statements once by hand against a
# SEPARATE database (see the runbook) — do NOT mount this into someone else's DB.
# =============================================================================
set -eu

: "${GRADING_MIGRATOR_PASSWORD:?GRADING_MIGRATOR_PASSWORD must be set in .env.prod}"
: "${GRADING_RUNTIME_PASSWORD:?GRADING_RUNTIME_PASSWORD must be set in .env.prod}"
: "${GRADING_AUDIT_READER_PASSWORD:?GRADING_AUDIT_READER_PASSWORD must be set in .env.prod}"

# Use the superuser + target DB the entrypoint already created.
psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     --set=migrator_pwd="$GRADING_MIGRATOR_PASSWORD" \
     --set=runtime_pwd="$GRADING_RUNTIME_PASSWORD" \
     --set=audit_reader_pwd="$GRADING_AUDIT_READER_PASSWORD" <<'SQL'
DO $$
DECLARE
  v_migrator_pwd     text := :'migrator_pwd';
  v_runtime_pwd      text := :'runtime_pwd';
  v_audit_reader_pwd text := :'audit_reader_pwd';
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_migrator') THEN
    EXECUTE format('CREATE ROLE grading_migrator LOGIN PASSWORD %L', v_migrator_pwd);
  ELSE
    EXECUTE format('ALTER ROLE grading_migrator LOGIN PASSWORD %L', v_migrator_pwd);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_runtime') THEN
    EXECUTE format('CREATE ROLE grading_runtime LOGIN PASSWORD %L', v_runtime_pwd);
  ELSE
    EXECUTE format('ALTER ROLE grading_runtime LOGIN PASSWORD %L', v_runtime_pwd);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_audit_reader') THEN
    EXECUTE format('CREATE ROLE grading_audit_reader LOGIN PASSWORD %L', v_audit_reader_pwd);
  ELSE
    EXECUTE format('ALTER ROLE grading_audit_reader LOGIN PASSWORD %L', v_audit_reader_pwd);
  END IF;

  -- grading_migrator must be able to CONNECT to the dedicated db immediately so
  -- Liquibase can run. Other privileges are granted by changelog 005.
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO grading_migrator', current_database());
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO grading_runtime', current_database());
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO grading_audit_reader', current_database());
END
$$;
SQL

echo "grading: F-04 roles (migrator/runtime/audit_reader) created with LOGIN."
