# PostgreSQL Role Grant Matrix — grading.hrlab.uz

**Status:** Implemented in Liquibase changelog
`db/changelog/control-plane/005-db-role-grants.yaml`.

**Remediates:** Security review finding **F-04 (High)** — audit append-only
defense-in-depth at the DB role layer.

## 1. Roles

| Role | Purpose | LOGIN | Used by |
|------|---------|-------|---------|
| `grading_migrator` | DDL + full DML. Owner of schema changes. | NOLOGIN by default; LOGIN attached per-env via Vault. | Liquibase migration runner (CI/CD + local migration job). |
| `grading_runtime` | Day-to-day DML. **NO UPDATE/DELETE on audit tables.** | NOLOGIN by default; LOGIN attached per-env via Vault. | Spring Boot application connection (`spring.datasource.username`). |
| `grading_audit_reader` | SELECT on audit tables only. | NOLOGIN by default; LOGIN attached per-env via Vault. | Audit query API; gated additionally by `AUDIT_READ` permission at app layer. |

> In the `test-roles` Liquibase context (Testcontainers + docker-compose
> only) the three roles are also given LOGIN + well-known dev passwords so
> tests can connect directly. **Never enable `test-roles` in production.**

## 2. Grant matrix

| Role | Control Plane (`tenants`, `users`, `roles`, `permissions`, `client_companies`, `user_tenant_memberships`, `user_project_assignments`, `user_roles`, `role_permissions`, `localization_messages`, …) | Tenant Business (`positions`, `evaluations`, `job_profiles`, `methodologies`, `salary_ranges`, … — Phase 2+) | Audit (`system_audit_log`, `tenant_audit_logs`) |
|------|---|---|---|
| `grading_migrator` | ALL (DDL + DML) | ALL (DDL + DML) | ALL (DDL + DML) |
| `grading_runtime` | SELECT, INSERT, UPDATE, DELETE | SELECT, INSERT, UPDATE, DELETE | **SELECT, INSERT only** (no UPDATE, no DELETE, no TRUNCATE) |
| `grading_audit_reader` | (none) | (none) | SELECT only |

PUBLIC is revoked from schema `public`; only the three roles above hold
`USAGE`. `grading_migrator` additionally holds `CREATE` on `public`.

`ALTER DEFAULT PRIVILEGES` is set so newly created tables in `public`
automatically grant the right set to `grading_runtime` (DML) and
`grading_migrator` (ALL). **Future audit-related tables** (e.g. monthly
partitions of `system_audit_log` once §15.3 of the blueprint lands)
must include a follow-up changeset that explicitly
`REVOKE UPDATE, DELETE, TRUNCATE ... FROM grading_runtime` to preserve
the append-only contract.

## 3. Defense-in-depth layers

The audit append-only guarantee is defended at four layers:

1. **JPA repository interface** — `SystemAuditLogRepository extends
   Repository<…>` (not `JpaRepository`), exposing only `save`, finders, and
   `count`. No `delete*`, no `saveAll`, no `@Modifying` queries.
   *Test:* `AuditAppendOnlyTest`.
2. **DB role grants** *(this document)* — `grading_runtime` lacks
   `UPDATE`, `DELETE`, `TRUNCATE` on `public.system_audit_log` and
   `public.tenant_audit_logs`.
   *Test:* `AuditRoleGrantsTest`.
3. **Hash chain** — each row stores `hash_current = SHA-256(prev || canonical
   payload)`; tampering is detectable on forensic review.
4. **WORM anchor (devops-sre, deferred)** — daily upload of the latest
   `hash_current` per tenant to an object-locked S3 bucket.

## 4. Connection-string handoff to devops-sre

Each environment requires **three** connection strings in its secret store
(Vault path examples below; final paths owned by devops-sre):

| Vault path | Role | When used |
|---|---|---|
| `secret/grading/<env>/db/migrator` | `grading_migrator` | Liquibase job / `liquibase update` step in CI/CD. Mounted into the migration init-container only. |
| `secret/grading/<env>/db/runtime` | `grading_runtime` | Spring Boot pod. Bound to `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`. |
| `secret/grading/<env>/db/audit_reader` | `grading_audit_reader` | Reserved for audit-query worker / pgAdmin readonly access. Optional in Phase 1; mandatory once `/api/v1/audit/search` ships. |

Production passwords are rotated by devops-sre; the Liquibase changeset
above **never** sets a password in deployed environments.

## 4a. Phase 2 trigger functions (no extra grants required)

`tenant-schema/004-phase2-constraints.yaml` introduces two PL/pgSQL
functions used only as trigger bodies:

| Function | Trigger | Table | Security |
|---|---|---|---|
| `prevent_department_cycle()` | `trg_prevent_department_cycle` BEFORE INSERT OR UPDATE OF parent_id | `departments` | SECURITY INVOKER (default) |
| `enforce_project_lock_immutability()` | `trg_enforce_project_lock` BEFORE UPDATE OF status | `projects` | SECURITY INVOKER (default) |

Both functions are invoked **from** triggers attached to tables on which
`grading_runtime` already holds INSERT/UPDATE. No additional `GRANT
EXECUTE ON FUNCTION` is needed: trigger invocation happens implicitly
as part of the DML statement and uses the calling user's privileges.

## 5. Local dev posture

`docker-compose.yml` boots Postgres with the superuser `grading_app`
(legacy local credential). The `test-roles` context attaches LOGIN +
well-known dev passwords to the three new roles, so a developer can:

```bash
# Connect as runtime user and verify the lockdown manually:
psql "postgresql://grading_runtime:grading_runtime_pwd@localhost:5432/grading_control_db" \
  -c "DELETE FROM public.system_audit_log;"
# => ERROR:  permission denied for table system_audit_log
```

A single superuser remains acceptable for local development per the
application-local.yml comment, but operators are encouraged to switch
the application's datasource username to `grading_runtime` locally to
exercise the same code path as production.
