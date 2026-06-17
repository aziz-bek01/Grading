-- =============================================================================
-- grading.hrlab.uz — LOCAL DEV Postgres bootstrap (first-init only).
-- =============================================================================
-- DEV-ONLY. NEVER use these credentials anywhere but a developer laptop.
--
-- The official postgres image runs every *.sql / *.sh in
-- /docker-entrypoint-initdb.d/ EXACTLY ONCE, on the first initialisation of an
-- EMPTY data volume. docker-compose.local.yml mounts THIS file there.
--
-- WHAT THIS DOES — only the bootstrap pieces Liquibase cannot do for itself:
--   1. Create the application database `grading_control_db`.
--   2. Create the bootstrap superuser role `grading_app` / `grading_app_pwd`
--      that the Spring `local` profile (application-local.yml) connects as.
--
-- WHY `grading_app` is a SUPERUSER here (and ONLY here):
--   Under the `local` profile a SINGLE connection must both RUN Liquibase
--   (DDL: CREATE EXTENSION pgcrypto, CREATE ROLE ..., schema objects, and
--   BYPASSRLS-style cross-tenant seed writes) AND serve runtime traffic — a
--   deliberate developer convenience documented in application-local.yml.
--   In every deployed environment this is split into grading_migrator (DDL)
--   and grading_runtime (DML), which Liquibase's `test-roles` context creates
--   with LOGIN + dev passwords AFTER this script (changelog 005.7). A developer
--   may then opt into role separation by switching the datasource user in
--   application-local.yml to grading_runtime / grading_runtime_pwd.
--
-- WHAT THIS DOES NOT DO:
--   It does NOT create the schema, tables, roles' grants, or seed data — that
--   is 100% Liquibase's job when the backend boots under the `local` profile
--   with contexts `control-plane,seeds,test-roles,mode-shared,dev`. The `dev`
--   context seeds the ACME / Beta demo tenants + the demo super-admin user so
--   the UI screens come up populated.
--
-- The container's POSTGRES_USER (the cluster superuser, see compose) owns the
-- entrypoint connection that runs this file, so CREATE DATABASE / CREATE ROLE
-- both succeed. Guarded so a re-run on a non-empty volume is harmless.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Bootstrap login role used by the Spring `local` profile.
--    Password is a CLEARLY-MARKED DEV PLACEHOLDER — matches
--    application-local.yml (username: grading_app / password: grading_app_pwd).
--    SUPERUSER is required so the single local connection can run all of
--    Liquibase's DDL + the test-roles role creation + dev cross-tenant seeds.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'grading_app') THEN
    -- DEV-ONLY password. Never reuse outside a local laptop.
    CREATE ROLE grading_app LOGIN SUPERUSER PASSWORD 'grading_app_pwd';
  END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- 2. Application database. Owned by grading_app so it can create everything
--    inside it. CREATE DATABASE cannot run inside a transaction/DO block, so it
--    is emitted conditionally via \gexec (guarded by a pg_database lookup to
--    stay idempotent on re-init).
-- ---------------------------------------------------------------------------
SELECT 'CREATE DATABASE grading_control_db OWNER grading_app'
 WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_database WHERE datname = 'grading_control_db')
\gexec

-- Make sure ownership is correct even if the DB already existed.
ALTER DATABASE grading_control_db OWNER TO grading_app;

\echo 'grading(local): grading_control_db + bootstrap superuser grading_app ready (DEV-ONLY).'
