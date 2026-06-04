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
-- IMPORTANT: psql performs :'var' / :"var" interpolation ONLY in plain SQL,
-- NOT inside dollar-quoted blocks ($$...$$). So the password values must NOT
-- live in a DO block (the server would receive a literal ":" and fail with
-- 'syntax error at or near ":"'). Each CREATE ROLE is built as a string so the
-- literal is interpolated by psql, then run with \gexec — guarded by a pg_roles
-- lookup so the script stays idempotent / safe to re-run.
SELECT current_database() AS db \gset

-- grading_migrator -> Liquibase (DDL)
SELECT format('CREATE ROLE grading_migrator LOGIN PASSWORD %L', :'migrator_pwd')
 WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_migrator') \gexec
ALTER ROLE grading_migrator LOGIN PASSWORD :'migrator_pwd';

-- grading_runtime -> the API at runtime (DML)
SELECT format('CREATE ROLE grading_runtime LOGIN PASSWORD %L', :'runtime_pwd')
 WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_runtime') \gexec
ALTER ROLE grading_runtime LOGIN PASSWORD :'runtime_pwd';

-- grading_audit_reader -> audit-query path (SELECT on audit tables)
SELECT format('CREATE ROLE grading_audit_reader LOGIN PASSWORD %L', :'audit_reader_pwd')
 WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_audit_reader') \gexec
ALTER ROLE grading_audit_reader LOGIN PASSWORD :'audit_reader_pwd';

-- All three need CONNECT immediately (Liquibase + API + audit reader).
GRANT CONNECT ON DATABASE :"db" TO grading_migrator;
GRANT CONNECT ON DATABASE :"db" TO grading_runtime;
GRANT CONNECT ON DATABASE :"db" TO grading_audit_reader;

-- ---------------------------------------------------------------------------
-- COLD-START privileges for grading_migrator (granted here, as the bootstrap
-- superuser, because changelog 005 cannot grant them in time — 005 runs AS
-- grading_migrator and only AFTER 001-004 have already created tables).
--
-- Why each is required for the very FIRST Liquibase run on an empty volume:
--   * CREATE ON SCHEMA public — Liquibase creates DATABASECHANGELOG /
--       DATABASECHANGELOGLOCK and control-plane tables (changelogs 001-004) in
--       `public`. Since PostgreSQL 15 `public` no longer grants CREATE to
--       non-owner roles, so without this the first migration dies with
--       "permission denied for schema public".
--   * CREATE ON DATABASE — changelog 001 runs
--       `CREATE EXTENSION IF NOT EXISTS pgcrypto`. pgcrypto is TRUSTED (PG13+),
--       installable by a non-superuser holding database-level CREATE.
--   * BYPASSRLS — 030-enable-rls applies FORCE ROW LEVEL SECURITY, which
--       exempts NOBODY by ownership. grading_migrator must behave as the
--       "DB-owner / superuser-equivalent" that BYPASSES tenant RLS so
--       migrations/seeds work across tenants. Settable only by a superuser —
--       which POSTGRES_USER is on first init.
-- Idempotent and additive; changelog 005 re-asserts the schema grants later.
-- ---------------------------------------------------------------------------
GRANT CREATE ON DATABASE :"db" TO grading_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO grading_migrator;
ALTER ROLE grading_migrator BYPASSRLS;
SQL

echo "grading: F-04 roles (migrator/runtime/audit_reader) created with LOGIN."
