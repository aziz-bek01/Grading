# PRD / Decision Doc — User Onboarding & Identity Provisioning (Zitadel)

Status: DRAFT for Product Owner decision
Owner: hr-product-owner
Audience: PO (decision), security-engineer, integration-engineer, backend-engineer, frontend-engineer, qa-engineer, devops-sre
Architecture refs: §8.1 (OAuth2/OIDC + JWT), §8.3 (role model), §8.5 (audit), §12 (DB / provisioning), §19 (SSO integration), ADR-006 (security model)
Scope note: This closes a real gap in the "Users & Access" feature. No salary scope. No code in this doc.

---

## 1. Problem statement (confirmed gap, with evidence)

Production authentication is self-hosted **Zitadel** OIDC (issuer `https://auth.hrlab.uz`). The backend is a pure OAuth2 **resource server**: it validates the JWT, then resolves the principal to a grading control-plane user **by email** (fallback `external_idp_subject == sub`).

- `JwtTenantContextResolver.resolveUser(...)` — order: `sub` as grading `users.id` (rare) → `email` (the Zitadel path) → `external_idp_subject`. If none match, it returns a `userId == null` context and the request fails closed.
- `InviteUserUseCase.invite(...)` creates ONLY grading DB rows on invite:
  - `public.users` (status `INVITED`, **`external_idp_subject = null`** — see the `new UserJpaEntity(..., null, ...)` call),
  - `user_tenant_memberships` (status `INVITED`),
  - `user_roles`.
  It does **not** call Zitadel, does **not** send an invite/verify email, and does **not** set `external_idp_subject`.
- There is **no Zitadel Management-API client** anywhere in the backend (no service-account config, no connector, no secret). `application.yml` only references the resource-server `issuer-uri` (set per-profile in prod).

**Consequence:** a user "added" through the product cannot actually log in in production unless their email **already exists** as a Zitadel account. "Add user" is half-built: it grants grading authorization to an identity that may not be authenticatable. The grading row sits in `INVITED` with no path to `ACTIVE` login.

The architecture defines **tenant** provisioning (§12.2) but is **silent on user onboarding / IdP-account creation**. This requires a product + architecture decision.

### Secondary gap — prod first-super-admin bootstrap
The only seed that creates a super-admin (`public.users` + membership + `HRLAB_SUPER_ADMIN` role) is `seeds-dev/001-dev-seed-tenants.yaml`, declared `context: dev`. Production's migrator runs contexts `control-plane,seeds,mode-shared` (`application.yml` `spring.liquibase.contexts`), which does **not** include `dev`. So a clean prod deploy has **zero admins** — and with no admin, no one can invite anyone. Chicken-and-egg.

---

## 2. Options analysis — how an invited user becomes Zitadel-authenticatable

Evaluation lenses per option: pros / cons, security implications (Management-API secret storage, who can invite, tenant isolation, email verification, offboarding symmetry), architecture fit (§8.1 / §8.3 / §12 / §19), MVP/ops reality (Zitadel self-hosted on a 2GB VPS).

### Option A — Backend calls the Zitadel Management API on invite
Backend holds a Zitadel **service-account** (PAT or client-credentials) with user-management scope. On invite it: creates the Zitadel human user, sets metadata, triggers Zitadel's built-in invite / verify-email flow, and stores the returned Zitadel user id in `external_idp_subject`.

- Pros
  - One-click product UX: the admin invites; the user gets a real email and can verify + set a passkey/password.
  - Grading stays the **system of record for authorization**; Zitadel is the **system of record for credentials** — clean split, matches §8.1.
  - `external_idp_subject` is populated at creation → login resolves by stable `sub`, not just email (more robust if a user later changes email).
  - Symmetric offboarding is possible: deactivate/remove the Zitadel user when the grading membership is revoked.
- Cons
  - Requires a **Management-API secret** in the backend (Vault per §21.4) + Zitadel SMTP configured for invite emails.
  - New failure mode: invite is now a **two-system transaction** (grading DB + Zitadel). Needs an outbox / retry / reconcile design or the two stores drift.
  - More moving parts on a 2GB VPS (SMTP, API throughput).
- Security implications
  - Secret storage: client-credentials in Vault, least-privilege Zitadel service-account (user create/deactivate only, scoped to the grading project/org), rotation policy. The secret is **platform-wide**, not per-tenant — a leak lets an attacker mint identities, so it is a high-value secret (threat model required).
  - Who can invite: enforced by grading RBAC/ABAC (`USER_INVITE` / `requireCanManageInTenant`), not by Zitadel. Backend remains the gatekeeper.
  - Tenant isolation: Zitadel users are global identities; **authorization scoping stays 100% in grading** (memberships/roles). Email uniqueness is global (already the case: `findByEmailIgnoreCase`).
  - Email verification: delegated to Zitadel's verify flow — good.
  - Offboarding symmetry: achievable (see §5).
- Architecture fit: Best fit for §8.1 + §19 (SSO/IdP federation) + §12 (provisioning lifecycle extended from tenant to user). Adds an Integration-module connector, consistent with §10.

### Option B — Admin pre-creates the user in Zitadel (or org self-registration); grading matches by email on first login
This is the **current de-facto behavior**. Document it as an explicit, supported **manual** flow: someone with Zitadel console access creates the account (or the client org allows self-registration on an allowlisted domain), then an admin invites the same email in grading; first login matches by email.

- Pros
  - **Zero new code, zero new secret.** Ships today.
  - No two-system transaction; each system is managed independently.
  - Good fallback / "manual mode" even if Option A is later built.
- Cons
  - Poor UX and high operational error rate: two disjoint manual steps, easy to mistype the email → user is `INVITED` forever with no working login and no signal why.
  - Doesn't scale to the target flow (super-admin onboards many client-company admins who each onboard staff).
  - `external_idp_subject` stays null unless backfilled on first login (it currently is not).
- Security implications
  - No Management-API secret to protect (plus).
  - "Who can invite" is split across two consoles → weaker governance, audit lives in two places.
  - Self-registration is risky unless strictly domain-allowlisted; otherwise anyone on a domain can create an authenticatable identity (then sits unauthorized in grading, but still a footprint).
  - Offboarding symmetry: entirely manual in two places → easy to forget the Zitadel side after grading revoke (residual login risk).
- Architecture fit: Compatible with §8.1 (resolver already supports it) but does not satisfy the product's self-serve onboarding intent; §19 SSO "domain mapping" is the principled version of self-registration.

### Option C — JIT (just-in-time) provisioning on first Zitadel login
Flip the order: Zitadel is the **system of record for identity**. On the first successful login whose email/domain is **allowlisted**, auto-create the grading `users` row (and optionally a default membership). The invite step in grading becomes optional / a pre-grant.

- Pros
  - No Management-API secret; backend stays resource-server-only.
  - Frictionless for federated client IdPs (§19 SSO) — user authenticates, grading row appears.
  - `external_idp_subject` naturally captured at first login.
- Cons
  - **Authorization-before-identity inversion risk:** a brand-new JIT user has no membership/roles → fails closed and sees nothing. So JIT alone does not deliver "invited user can work"; it must be paired with a pre-grant (invite that creates membership/roles keyed by email, materialized on first login).
  - Allowlist governance becomes security-critical: a too-broad domain allowlist auto-creates users for anyone in the domain.
  - Harder to answer "who are my pending users?" before they have ever logged in (no Zitadel-side visibility from grading).
- Security implications
  - No platform secret (plus).
  - Tenant isolation: must ensure a JIT-created user does **not** get any tenant access except what an admin explicitly pre-granted; default = no membership.
  - Email verification: relies on Zitadel having verified before issuing tokens — must confirm Zitadel config enforces `email_verified`.
  - Offboarding symmetry: grading can disable its row, but cannot stop the Zitadel login (token still issued) → must combine with Zitadel-side deactivation or rely on grading fail-closed (user logs in but sees nothing). Weaker than A.
- Architecture fit: Strong fit for §19 federated-SSO clients (enterprise tier), weaker for the HRLab-managed self-serve flow on the shared Zitadel.

### Option D — Hybrid
Use **A as the default managed flow** on HRLab's own Zitadel (super-admin and HRLab-managed client admins get a real invite email), and **C (JIT + pre-grant) for enterprise clients federating their own IdP** (§19 SSO). **B remains the documented manual fallback** for break-glass and for orgs where HRLab cannot create accounts.

- Pros: matches the architecture's tiered reality (shared control plane + enterprise isolation) and the two distinct onboarding realities (HRLab-managed vs client-federated).
- Cons: largest surface; do not build all paths in MVP 1 — sequence them.
- Architecture fit: Best long-term fit (§8.1 + §19 + ADR-006). Requires discipline to not over-build now.

---

## 3. Recommendation

**Adopt Option A now (Management-API connector) as the single onboarding path for MVP 1, and design the model so Option D (add C/federation for enterprise clients) is a later additive step — not a rewrite.** Keep Option B explicitly documented as the supported **break-glass / manual fallback**.

Rationale:
1. The MVP reality is exactly A's sweet spot: HRLab runs its own Zitadel; a super-admin onboards client-company admins, who onboard their own staff. That is a **managed** invite flow needing a real email — B's two-console dance does not scale and silently strands users in `INVITED`.
2. A keeps the architecture's separation intact: grading = authorization system of record (§8.3 RBAC/ABAC stays the gatekeeper), Zitadel = credential system of record (§8.1). It only **completes** the lifecycle the resolver already assumes (it already reads `external_idp_subject`).
3. A makes offboarding **symmetric** (§5), closing a real residual-access risk that B and C leave open.
4. The one real cost — a platform Management-API secret + two-system consistency — is a known, boundable problem (Vault per §21.4, outbox/reconcile). It is worth paying once to make every future invite one click.
5. C is deliberately deferred: it shines for client-federated IdPs (enterprise tier, §19), which is not the MVP shape. Building C first would still require a pre-grant to make invited users useful, so it does not remove A's work — it adds to it.

Bootstrap (secondary gap) recommendation: see §6 — a **prod-safe one-time bootstrap** (env-gated changeset or an admin CLI/manual runbook), creating exactly one super-admin whose email maps to a manually-created Zitadel account. This is the only place Option B is acceptable permanently, because the very first admin cannot be invited by anyone.

---

## 4. Scope of the recommended option (Option A)

### 4.1 User stories + acceptance criteria

#### US-1 — Invite creates a real, authenticatable identity
As an HRLab Super Admin / Client Company Admin,
I want inviting a user to also create their Zitadel account and send a verify-email invite,
so that the invited person can actually log in without any second manual step.

- Given I have `USER_INVITE` and `requireCanManageInTenant` for the target tenant,
  When I invite `jane@client.uz` with role `CLIENT_HR_SPECIALIST`,
  Then a grading `public.users` row is created (status `INVITED`), a Zitadel human user is created, `external_idp_subject` is set to the Zitadel user id, a Zitadel invite/verify email is sent, and audit events `USER_CREATED`, `USER_INVITED`, `USER_MEMBERSHIP_ADDED`, `USER_ROLE_ASSIGNED`, plus a new `USER_IDP_ACCOUNT_CREATED` are recorded.
- Given the email already exists in grading as an ACTIVE user,
  When I invite that email into a new tenant,
  Then no new Zitadel user is created (reuse existing `external_idp_subject`), only membership/roles are added, and audit reflects `USER_MEMBERSHIP_ADDED` (no `USER_IDP_ACCOUNT_CREATED`).
- Given the email already exists in Zitadel but not in grading,
  When I invite it,
  Then the backend links the existing Zitadel user (sets `external_idp_subject` from the Zitadel lookup) instead of creating a duplicate, and records `USER_IDP_ACCOUNT_LINKED`.
- Given the Zitadel Management API is unreachable,
  When I invite,
  Then the grading invite either (a) is not committed and returns a clear retryable error, or (b) is committed as `INVITED` with `idp_provisioning_status = PENDING` and a reconciler completes it — the chosen mode is explicit and the user is never left silently un-loginable. (security-engineer + integration-engineer to confirm which; recommend outbox + PENDING.)

#### US-2 — Invited user completes sign-in
As an invited user,
I want to click the verify link, set my credential, and log in,
so that I land in grading with exactly the access I was granted.

- Given I completed Zitadel verification,
  When I log in for the first time,
  Then the token's `sub` matches my `external_idp_subject`, the resolver finds my user, my grading status flips `INVITED → ACTIVE` (and membership `INVITED → ACTIVE`), and I see only the tenant(s)/roles granted.
- Given I have no grading membership yet (race / pre-grant pending),
  When I log in,
  Then I fail closed (generic 401/404, no data) — consistent with the current resolver.

#### US-3 — Offboarding is symmetric (the deactivation counterpart)
As an admin,
I want revoking/disabling a user to also disable their ability to authenticate,
so that an offboarded person cannot log in even though grading would already deny them data.

- Given a user with an active membership,
  When I revoke their **last remaining** membership (or set the user `DISABLED` via PATCH),
  Then grading marks them revoked/disabled AND the backend deactivates (not deletes) the Zitadel user, recording `USER_IDP_ACCOUNT_DEACTIVATED`. Salary permission is force-cleared (already done in `RevokeMembershipUseCase`).
- Given a user still has at least one other active membership,
  When I revoke one membership,
  Then the Zitadel account stays active (they can still log in to their other tenant) and only grading authorization shrinks.
- Given a previously disabled user is re-activated in grading,
  When an admin re-enables them,
  Then the Zitadel account is re-activated and `USER_IDP_ACCOUNT_REACTIVATED` is recorded.

### 4.2 Permissions involved (no new grants needed; reuse §8.3 model)
- `USER_INVITE` / `USER_ROLE_ASSIGN_HRLAB` / `requireCanManageInTenant` — already enforced by `UserManagementPolicy`. Creating the Zitadel account is a side effect of an already-authorized invite; it introduces **no new user-facing permission**.
- The Zitadel service-account is a **system credential**, not a user role. It must never be reachable via any user-facing endpoint.

### 4.3 Audit events (extends §8.5)
New, append-only, on top of existing `USER_CREATED / USER_INVITED / USER_MEMBERSHIP_ADDED / USER_ROLE_ASSIGNED / USER_MEMBERSHIP_REVOKED / USER_UPDATED`:
- `USER_IDP_ACCOUNT_CREATED` (entityType `User`, stores Zitadel user id in `after`, never PII beyond email)
- `USER_IDP_ACCOUNT_LINKED`
- `USER_IDP_ACCOUNT_DEACTIVATED`
- `USER_IDP_ACCOUNT_REACTIVATED`
- `USER_IDP_PROVISIONING_FAILED` (for reconcile/observability)
All carry `tenant_id`, `actor_user_id`, hash-chained per §8.5.

### 4.4 Data requirements
- `public.users.external_idp_subject` — already exists; must be **populated** by invite (currently null).
- Optional new column `public.users.idp_provisioning_status` (`PENDING | PROVISIONED | FAILED | DEACTIVATED`) to support the outbox/reconcile mode in US-1 last AC. database-architect to confirm vs. reusing existing `status`.
- No salary fields touched. No tenant-data-plane changes.

### 4.5 Which agents / layers implement it
- **security-engineer**: threat model for the platform Management-API secret (storage in Vault §21.4, least-privilege Zitadel service-account scope, rotation, blast-radius), confirm invite transaction mode (commit-then-reconcile vs. all-or-nothing), confirm Zitadel enforces `email_verified` before token issuance, define abuse cases (invite spam, enumeration).
- **integration-engineer**: the Zitadel Management-API connector (create user, link existing, trigger invite/verify, deactivate, reactivate), retry/outbox/reconciler, config + secret wiring, integration audit events. This is a §19-style integration adapter living in the Integration module.
- **backend-engineer**: extend `InviteUserUseCase` / `RevokeMembershipUseCase` / `PatchUserUseCase` to call the connector and emit new audit events; first-login `INVITED → ACTIVE` materialization in the resolver/`GetCurrentUserUseCase`; `idp_provisioning_status` handling.
- **frontend-engineer**: surface invite states — `INVITED (email sent)`, `IDP pending/failed (retry)`, `ACTIVE`; show "resend invite"; localized (4 langs) loading/empty/error/no-access states.
- **qa-engineer**: see §7.
- **devops-sre**: provision the Zitadel service-account + Vault secret per environment; configure Zitadel SMTP for invite emails; own bootstrap runbook (§6).
- **database-architect**: decide `idp_provisioning_status` column vs. status reuse; migration changeset.

### 4.6 Dependencies
- Zitadel SMTP must be configured (invite emails) — devops-sre, blocks US-1/US-2.
- Zitadel service-account with user-management scope + Vault secret per env — blocks all of Option A.
- Bootstrap (§6) must exist before any invite can happen in prod (no admin = no inviter).

### 4.7 Risks + mitigations
- **Two-system drift** (grading row exists, Zitadel user doesn't, or vice versa) → outbox + reconciler + `idp_provisioning_status`; never leave a user silently un-loginable.
- **Management-API secret compromise** (mint/deactivate any identity) → Vault, least-privilege scope, rotation, alerting on unusual create/deactivate volume; secret unreachable from user endpoints.
- **Orphaned Zitadel accounts after grading revoke** → symmetric deactivation (US-3) + periodic reconcile of grading-disabled vs. Zitadel-active.
- **Invite spam / email enumeration** → rate-limit invite, keep generic responses (resolver already avoids leaking "email unknown").
- **2GB VPS pressure** (SMTP + API) → async invite (worker), backpressure on bulk invites.
- **Email change divergence** (grading email vs. Zitadel email) → prefer `external_idp_subject` (`sub`) as the join key once populated; treat email as display/secondary match.

### 4.8 Out of scope
- Salary permission/encryption (separate domain).
- Enterprise client-IdP federation / SAML (that is the later Option C/D step, §19).
- Self-registration flows.
- Bulk CSV user import (MVP 2+).

---

## 5. Offboarding / deactivation symmetry (explicit)
Today grading offboarding is one-sided: `RevokeMembershipUseCase` soft-revokes the membership and clears salary permission; `PatchUserUseCase` can set the user `DISABLED`. **Neither touches Zitadel**, so an offboarded user can still authenticate (grading then fails them closed — defense in depth, but a real residual login footprint and an audit-symmetry gap). Option A closes this: deactivating the **last** membership or disabling the user deactivates the Zitadel account (US-3). Deactivate, do not delete, to preserve audit/history per §8.5. Re-activation is symmetric.

---

## 6. Prod first-super-admin bootstrap (secondary gap) — recommendation

Problem recap: the super-admin seed is `context: dev`; prod runs `control-plane,seeds,mode-shared` → clean prod has no admin, and no admin means no one can invite. Chicken-and-egg.

Design-level recommendation (pick one; recommended = B1):

- **B1 (recommended) — env-gated one-time bootstrap changeset.** A dedicated changeset under a new `bootstrap` context (NOT `dev`), parameterized by env vars `GRADING_BOOTSTRAP_ADMIN_EMAIL` (+ optional name/locale), guarded by a precondition `sqlCheck: 0 users with HRLAB_SUPER_ADMIN`. devops-sre runs the migrator **once** with `-Dliquibase.contexts=...,bootstrap` against a freshly-deployed prod, supplying the real super-admin email. The matching Zitadel account is created manually in the Zitadel console (the only permanent, acceptable use of Option B — the first admin literally cannot be invited). After first login, the bootstrap context is dropped from the standard contexts.
  - Pros: auditable, idempotent (precondition self-disables), no hardcoded identity in the image, no special endpoint.
  - Cons: requires a deliberate one-time ops step (acceptable; it is a once-per-environment action).
- **B2 — admin bootstrap CLI / Spring profile task.** A `bootstrap-admin` profile/command that, only when zero super-admins exist, creates the user+membership+role from env vars (and, with Option A live, also creates+links the Zitadel account in one shot). Pros: also creates the IdP side. Cons: more code; must be impossible to run when an admin already exists.
- **B3 (reject) — ship a default admin in a prod seed.** Hardcoded identity, exactly the anti-pattern the `context: dev` guard avoids. Do not do this.

Recommendation: **B1 now** (fastest, no new code), and fold the IdP-side creation into **B2** once Option A's connector exists so even the first admin is fully provisioned in one step. Either way: exactly one super-admin, env-driven, precondition-guarded, audited, and the Zitadel side for the very first admin is created manually.

---

## 7. QA acceptance pack (Given/When/Then)
- IdP create on invite: invited email → Zitadel user exists + `external_idp_subject` set + verify email sent + audit `USER_IDP_ACCOUNT_CREATED`.
- Existing-Zitadel link: pre-existing Zitadel email → linked, not duplicated → audit `USER_IDP_ACCOUNT_LINKED`.
- First login activation: verified invitee logs in → grading `INVITED → ACTIVE`, sees only granted tenant/roles.
- Fail-closed (no membership): authenticated principal with no grading membership → 401/404, no data, no leak that email is unknown.
- Offboarding symmetry: revoke last membership → Zitadel deactivated + `USER_IDP_ACCOUNT_DEACTIVATED`; revoke one-of-many → Zitadel stays active.
- Tenant isolation: Tenant A admin cannot invite/manage into Tenant B; cross-tenant attempt audited per §22.2.
- Secret isolation: no user-facing endpoint exposes or uses the Management-API secret; secret only in Vault.
- Reconcile: simulate Management-API outage on invite → user ends `PENDING`, reconciler resolves to `PROVISIONED`, never silently un-loginable.
- Bootstrap: clean prod + `bootstrap` context + env email → exactly one super-admin; re-run is a no-op (precondition); without the context, no admin is created.
- Localization: invite UI states (sent / pending / failed / active) render in ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US.

---

## 8. Decision request to the Product Owner
1. Approve **Option A now**, Option D later, Option B as documented fallback? (recommended)
2. Approve invite transaction mode: **commit-then-reconcile with `idp_provisioning_status`** (recommended) vs. strict all-or-nothing?
3. Approve bootstrap approach **B1 now → B2 after connector** (recommended)?
4. Confirm devops-sre to configure Zitadel SMTP + service-account/Vault secret as a blocking dependency.
