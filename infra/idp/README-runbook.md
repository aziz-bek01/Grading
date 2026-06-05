# auth.hrlab.uz — Self-hosted OIDC Identity Provider (ZITADEL) Runbook

Lightweight, self-hosted OIDC IdP for **grading.hrlab.uz**, co-located on the
**existing 2 GB shared VPS `95.46.96.210`** that already runs:

- `quiz.hrlab.uz` — host Python process on `:8080` (NOT touched)
- the grading stack (docker): `grading-api` (127.0.0.1:18080),
  `grading-frontend` (127.0.0.1:18081), `grading-postgres` (network
  `grading-prod`, no host port, image `postgres:16-alpine`) (NOT touched)

The IdP is fully **namespaced `zitadel-*`**, binds **only** to
`127.0.0.1:18082`, is **mem-limited**, and **REUSES** the existing
`grading-postgres` instance via a **separate database + role** (so we keep one
Postgres process). The **host nginx** fronts it with TLS (certbot), exactly like
the grading vhost.

> Files in this folder:
> - `docker-compose.idp.yml` — the ZITADEL services (no secrets)
> - `.env.idp.example` — env template (no secrets)
> - `auth.hrlab.uz.nginx.conf` — host nginx vhost (HTTP bootstrap; certbot adds 443)
> - `README-runbook.md` — this file

---

## 0. Product choice — ZITADEL (with the Logto note)

**Chosen: ZITADEL** (Go, single binary, OIDC-certified, built-in MFA/passkeys,
admin console + management API, multi-tenant "organizations"). It is the lightest
*full-featured* OIDC IdP that still ships an admin UI and MFA.

**Why not Logto on 2 GB:** Logto is also lightweight (Node), but it runs as **two
processes** (core API + admin console) and its Node runtime baseline tends to sit
a touch higher and spikier under load than ZITADEL's single Go binary. On a box
with only ~900 MB free, a single Go process with a hard 512M cap is the safer
bet. **Verdict: default to ZITADEL.** If you later hit memory pressure you cannot
tune away, Logto is a reasonable fallback — but it would not obviously *save* RAM
here, so there is no reason to deviate now.

> ZITADEL also REUSES the existing Postgres (no second DB engine, no Redis), which
> is the single biggest RAM win on this box.

---

## 1. CRITICAL: issuer, JWKS, audience, and token-type compatibility

This is the part that breaks silently if you skip it. The grading backend
(`SecurityConfig.jwtDecoder`) validates JWT access tokens **locally** and enforces
THREE things. ZITADEL differs from Keycloak on all three — you MUST account for
each:

| What the backend does | Keycloak behaviour (current default) | ZITADEL behaviour | Action |
|---|---|---|---|
| Enforces `iss == GRADING_JWT_ISSUER_URI` | `https://auth.hrlab.uz/realms/grading` | `https://auth.hrlab.uz` (BARE domain — no `/realms/...`) | **Set `GRADING_JWT_ISSUER_URI=https://auth.hrlab.uz`** |
| Derives JWKS as `issuer + /protocol/openid-connect/certs` unless `jwk-set-uri` is set | matches Keycloak path | ZITADEL JWKS is `https://auth.hrlab.uz/oauth/v2/keys` | **Set `jwk-set-uri` explicitly (see §1.1)** |
| Requires `aud` to contain exactly `GRADING_JWT_AUDIENCE` (default `grading.hrlab.uz`) | you can set a custom `aud` mapper | ZITADEL `aud` contains the **Project ID** + client IDs, NOT an arbitrary string | **Set `GRADING_JWT_AUDIENCE=<ZITADEL Project ID>`** (see §1.2) |
| Validates locally via JWKS — token MUST be a JWT, not opaque | n/a | ZITADEL apps default to **JWT** for OIDC web/SPA, but **confirm** the app's token type = JWT | **Set the SPA app token type = JWT** (see §6) |

### 1.1 The JWKS-URI problem (the one that needs a 1-line compose change)

`docker-compose.prod.yml` injects only `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`
and `..._AUDIENCE` into `grading-api`. It does **not** inject `..._JWK_SET_URI`.
Spring only sees env vars that compose lists under `environment:`, so putting the
key in `.env.prod` alone is **not enough** — the variable must be added to the
`grading-api.environment:` block.

Because that file is **outside `infra/idp/`**, this runbook does **not** edit it.
You have two clean options — pick ONE:

- **Option A (recommended, 1-line edit, no app code):** add this single line to
  the `grading-api:` `environment:` block in `docker-compose.prod.yml`, then add
  the value to `.env.prod`:
  ```yaml
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: ${GRADING_JWT_JWK_SET_URI}
  ```
  ```dotenv
  # /opt/grading/.env.prod
  GRADING_JWT_JWK_SET_URI=https://auth.hrlab.uz/oauth/v2/keys
  ```
  This is a deploy-config change, not application code — it stays within the
  DevOps remit. (It is the only edit to a grading file required to adopt ZITADEL.)

- **Option B (zero edits, requires backend agent):** add a tiny config branch so
  the backend derives the ZITADEL JWKS path when the issuer is the bare domain.
  This is application code → hand to the **backend-engineer**. Slower; Option A is
  preferred for a one-env-change cutover.

> Without one of these, `grading-api` will fetch keys from
> `https://auth.hrlab.uz/protocol/openid-connect/certs` → **404** → **every token
> fails validation**. This is the #1 cutover gotcha.

### 1.2 The audience problem

ZITADEL's access-token `aud` claim carries the **Project resource ID** (and the
client ID), not an arbitrary domain string. So `GRADING_JWT_AUDIENCE=grading.hrlab.uz`
will **not** match. Two ways to satisfy the backend's exact-match `aud` validator:

- **Recommended:** after you create the ZITADEL **Project** (§6), copy its
  **Project ID** (a long numeric resource id) and set on the VPS:
  ```dotenv
  GRADING_JWT_AUDIENCE=<ZITADEL_PROJECT_ID>
  ```
  Ensure the SPA app requests the project into the audience by including the scope
  `urn:zitadel:iam:org:project:id:<PROJECT_ID>:aud` (configure the SPA to send it,
  or mark the project so its ID is always added to `aud`). This is the canonical
  ZITADEL pattern.
- **Alternative:** keep `GRADING_JWT_AUDIENCE=grading.hrlab.uz` and add a ZITADEL
  **Action** that injects `grading.hrlab.uz` into the token's `aud`. More moving
  parts; prefer the Project-ID approach.

> Net result on the VPS `.env.prod`, the three values to set:
> ```dotenv
> GRADING_JWT_ISSUER_URI=https://auth.hrlab.uz
> GRADING_JWT_JWK_SET_URI=https://auth.hrlab.uz/oauth/v2/keys   # + the 1-line compose change (Option A)
> GRADING_JWT_AUDIENCE=<ZITADEL_PROJECT_ID>
> ```

---

## 2. Create the IdP database + role in grading-postgres (REUSE, do NOT add a 2nd Postgres)

Run these AS the grading-postgres superuser (`grading_owner` / `POSTGRES_SUPER_USER`
from the grading `.env.prod`). This creates a SEPARATE database + roles for
ZITADEL inside the SAME instance. It NEVER touches `grading_control_db`.

```sh
# On the VPS. Exec into the running grading-postgres container as its superuser.
# (Substitute the real superuser name from /opt/grading/.env.prod -> POSTGRES_SUPER_USER.)
docker exec -it grading-postgres-prod \
  psql -v ON_ERROR_STOP=1 -U grading_owner -d postgres
```

Then, in the psql prompt, run (replace the two passwords with the ones you put in
`.env.idp`):

```sql
-- Unprivileged runtime role used by the long-running `zitadel` service.
CREATE ROLE zitadel        LOGIN PASSWORD 'REPLACE_ME_zitadel_runtime_password';

-- Admin role used ONLY by the one-shot init/setup (DDL). Separate credential.
CREATE ROLE zitadel_admin  LOGIN PASSWORD 'REPLACE_ME_zitadel_admin_password';

-- ZITADEL's OWN database, owned by the admin role.
CREATE DATABASE zitadel OWNER zitadel_admin;

-- Runtime role needs to connect; ZITADEL's setup grants the rest of the schema
-- privileges inside its own DB.
GRANT CONNECT ON DATABASE zitadel TO zitadel;

\q
```

Then grant the runtime role schema access inside the new DB (ZITADEL creates its
own schema; give the runtime role usage so the service can read/write):

```sh
docker exec -it grading-postgres-prod \
  psql -v ON_ERROR_STOP=1 -U grading_owner -d zitadel -c \
  "GRANT ALL ON SCHEMA public TO zitadel_admin;
   ALTER DATABASE zitadel OWNER TO zitadel_admin;"
```

> Why two roles: `zitadel_admin` runs DDL during init/setup (and owns the DB);
> `zitadel` is the least-privilege runtime principal. Both are 100% separate from
> the grading roles. If you prefer fewer roles, point `ZITADEL_DATABASE_POSTGRES_ADMIN_*`
> at `grading_owner` for the one-shots only — but a dedicated admin is cleaner.

Verify the DB is isolated:

```sh
docker exec -it grading-postgres-prod psql -U grading_owner -d postgres -c "\l" | grep -E 'zitadel|grading_control_db'
# Expect TWO separate databases: grading_control_db AND zitadel.
```

---

## 3. Put the IdP files on the VPS + generate the masterkey

```sh
# A dedicated dir for the IdP compose project (separate from /opt/grading).
sudo mkdir -p /opt/idp
sudo chown "$USER":"$USER" /opt/idp
cd /opt/idp

# Copy these three files here (scp from your machine, or git checkout this folder):
#   docker-compose.idp.yml
#   .env.idp.example
#   auth.hrlab.uz.nginx.conf
cp /path/to/repo/infra/idp/docker-compose.idp.yml .
cp /path/to/repo/infra/idp/.env.idp.example .
cp /path/to/repo/infra/idp/auth.hrlab.uz.nginx.conf .

# Create the real env file (NEVER commit it).
cp .env.idp.example .env.idp
chmod 600 .env.idp

# Generate the 32-char masterkey and paste it into ZITADEL_MASTERKEY in .env.idp.
openssl rand -base64 24 | head -c 32 ; echo
# -> copy the 32 chars into ZITADEL_MASTERKEY=...

nano .env.idp
# Set: ZITADEL_MASTERKEY, the two DB passwords (matching §2), ExternalDomain,
#      the FirstInstance admin username/password/email.
```

Confirm the grading network exists (ZITADEL attaches to it as external):

```sh
docker network ls | grep grading-prod   # must already exist (created by grading stack)
```

---

## 4. Initialise + bring up ZITADEL

```sh
cd /opt/idp
C="docker compose -f docker-compose.idp.yml --env-file .env.idp"

# 4.1 One-shot: prepare the DB (init) then run migrations + create the first
#     instance (setup). Both exit 0 on success.
$C --profile init  run --rm zitadel-init
$C --profile setup run --rm zitadel-setup

# 4.2 Start the long-running IdP (binds 127.0.0.1:18082).
$C up -d zitadel

# 4.3 Status + logs.
$C ps
$C logs -f zitadel        # watch for "server is listening" / no DB errors
```

Local sanity check (TLS not required yet — this hits the container directly):

```sh
# Discovery via the container, faking the external Host so issuer URLs render:
curl -fsS -H 'Host: auth.hrlab.uz' http://127.0.0.1:18082/.well-known/openid-configuration | head
# Expect JSON with "issuer":"https://auth.hrlab.uz".
```

---

## 5. DNS + TLS (host nginx + certbot) — user action for DNS

### 5.1 DNS (USER ACTION — do this in the hrlab.uz DNS zone)

Add an **A record** (do not touch the other subdomains' records):

```
auth   A   95.46.96.210
```

Verify it has propagated to the VPS:

```sh
dig +short auth.hrlab.uz     # must return 95.46.96.210
```

### 5.2 Install the nginx vhost + obtain TLS

```sh
sudo cp /opt/idp/auth.hrlab.uz.nginx.conf /etc/nginx/sites-available/auth.hrlab.uz
sudo ln -sf ../sites-available/auth.hrlab.uz /etc/nginx/sites-enabled/auth.hrlab.uz

# Validate BEFORE reload; protects quiz + grading vhosts.
sudo nginx -t && sudo systemctl reload nginx

# certbot --nginx rewrites the auth vhost in place to add the 443 block +
# HTTP->HTTPS redirect (same managed pattern as grading/quiz).
sudo certbot --nginx -d auth.hrlab.uz --non-interactive --agree-tos \
  -m az.asqarov@gmail.com --redirect

sudo nginx -t && sudo systemctl reload nginx
```

### 5.3 Verify discovery over real HTTPS (the acceptance check)

```sh
curl -fsS https://auth.hrlab.uz/.well-known/openid-configuration | python3 -m json.tool
```

You MUST see HTTP 200 and these keys with the right values:

- `"issuer": "https://auth.hrlab.uz"`
- `"jwks_uri": "https://auth.hrlab.uz/oauth/v2/keys"`
- `"authorization_endpoint": "https://auth.hrlab.uz/oauth/v2/authorize"`
- `"token_endpoint": "https://auth.hrlab.uz/oauth/v2/token"`
- `"end_session_endpoint": "https://auth.hrlab.uz/oidc/v1/end_session"`
- `"userinfo_endpoint": "https://auth.hrlab.uz/oidc/v1/userinfo"`

Also confirm JWKS serves keys:

```sh
curl -fsS https://auth.hrlab.uz/oauth/v2/keys | python3 -m json.tool   # expect {"keys":[ ... ]}
```

---

## 6. Create the org, project, and PUBLIC SPA client (ZITADEL console)

Open `https://auth.hrlab.uz/ui/console` and log in with the FirstInstance admin
(`admin` / the password you set in `.env.idp`). Turn on **MFA/passkey** for the
admin when prompted.

1. **Organization:** the FirstInstance org `HRLab` already exists. (You can create
   per-tenant orgs later for multi-tenant client isolation.)
2. **Project:** Projects → **Create** → name `grading`.
   - Open the project → copy the **Resource Id (Project ID)**. This is the value
     for `GRADING_JWT_AUDIENCE` (see §1.2). Record it.
   - Project settings → enable **"Assert roles on authentication"** if you want
     role claims, and ensure the project ID can be added to token audiences.
3. **Application (PUBLIC SPA, Authorization Code + PKCE):**
   Inside the `grading` project → Applications → **Create** →
   - Type: **User Agent (SPA)** / Web with **PKCE** (public client, **no client
     secret**).
   - Auth method: **PKCE** (Authorization Code + PKCE).
   - **Redirect URI:** `https://grading.hrlab.uz/auth/callback`
   - **Post-logout redirect URI:** `https://grading.hrlab.uz`
   - **Token type: JWT** (CRITICAL — see §1; opaque tokens cannot be validated
     locally by the backend). Set this in the app's Token settings.
   - Dev mode: **off** in production (forces https redirect URIs).
   - Save → copy the **Client ID** (the SPA needs it; there is NO secret for a
     PKCE public client).
4. **Audience scope:** so issued access tokens carry the project in `aud`,
   configure the SPA to request the scope:
   `openid profile email urn:zitadel:iam:org:project:id:<PROJECT_ID>:aud`
   (the frontend agent wires this into the OIDC client config).
5. **Test user:** Users → **Create** → a human user (e.g. `tester@hrlab.uz`),
   set a password, verify email, optionally grant a project role. Use this to run
   the end-to-end login → token → `grading-api` smoke test.

> The exact Client ID + Project ID are produced here at runtime; record both. The
> frontend agent needs: issuer `https://auth.hrlab.uz`, the **Client ID**, the
> redirect/post-logout URIs above, and the audience scope.

---

## 7. Wire grading-api to ZITADEL (the exact env changes + roll)

On the VPS, edit `/opt/grading/.env.prod`:

```dotenv
# --- ZITADEL OIDC (replaces the Keycloak realm values) ---
GRADING_JWT_ISSUER_URI=https://auth.hrlab.uz
GRADING_JWT_AUDIENCE=<ZITADEL_PROJECT_ID>           # the Project Resource Id from §6
GRADING_JWT_JWK_SET_URI=https://auth.hrlab.uz/oauth/v2/keys
```

Apply the **one-line compose change** (Option A from §1.1) to
`/opt/grading/docker-compose.prod.yml` — add to the `grading-api:` `environment:`
block:

```yaml
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: ${GRADING_JWT_JWK_SET_URI}
```

> NOTE: the grading CD only overwrites the `IMAGE_TAG` line of `.env.prod` and
> scp's the repo's `docker-compose.prod.yml` over the VPS copy on each deploy. So
> if you make these changes ONLY on the VPS, the next deploy will REVERT the
> compose edit. To make them durable, also land Option A (1-line) + the example
> env keys in the repo via the backend/DevOps PR. Until then, re-apply after each
> deploy or pause CD.

Roll grading-api (no DB change, no migration):

```sh
cd /opt/grading
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"
$C up -d grading-api
$C logs -f grading-api          # confirm clean start, no JWKS 404 on first token

# Health:
curl -fsS http://127.0.0.1:18080/actuator/health/readiness
```

End-to-end smoke (the real gate): log into `https://grading.hrlab.uz` with the
test user, confirm the SPA gets a **JWT** access token from
`https://auth.hrlab.uz/oauth/v2/token`, and that a `grading-api` call returns 200
(not 401). A 401 with a JWKS/issuer/aud error in the api logs means one of the
three §1 values is wrong.

---

## 8. RAM / memory assessment (2 GB box)

See the "MEMORY/RAM assessment" section reported separately; in short:

- ZITADEL expected steady RSS: **~150–250 MB**. Hard limit set to **512M**
  (`zitadel` service). The `init`/`setup` one-shots are capped at 256M/384M and
  exit, so they don't add steady cost.
- No second Postgres (reused), no Redis → the only NEW steady consumer is the one
  ZITADEL process. Postgres gains a small per-connection overhead for ZITADEL's
  pool (a few MB) inside its existing `max_connections=50` budget.
- **Monitor:** `docker stats --no-stream` (watch `zitadel` MEM USAGE vs 512M),
  `free -m` (keep >150 MB free), and `dmesg | grep -i oom` / `journalctl -k`.
- **Trigger to bump to 4 GB:** sustained free RAM < 100 MB, any OOM kill of
  `zitadel` or a neighbour, or `zitadel` repeatedly pinned at the 512M ceiling
  during normal logins (not just first projection build).

---

## 9. Rollback (remove zitadel-* WITHOUT touching grading or quiz)

```sh
cd /opt/idp
C="docker compose -f docker-compose.idp.yml --env-file .env.idp"

# 9.1 Stop + remove ONLY the zitadel containers (compose project is isolated;
#     it never owns grading containers, and grading-prod is external so it is
#     NOT removed).
$C down
# (down removes zitadel, zitadel-init, zitadel-setup containers + the compose's
#  default resources, but NOT the external grading-prod network and NOT any
#  grading/quiz container.)

# 9.2 Verify the grading stack + quiz are untouched.
docker ps --format '{{.Names}}' | grep -E 'grading|zitadel'   # grading-* still up; no zitadel-*
curl -fsS http://127.0.0.1:18080/actuator/health/readiness    # grading-api OK
curl -fsS https://grading.hrlab.uz/healthz                    # grading still served
# quiz.hrlab.uz still served (host process untouched):
curl -fsS https://quiz.hrlab.uz/ -o /dev/null -w '%{http_code}\n'

# 9.3 (Optional) drop the ZITADEL database + roles from grading-postgres. ONLY
#     if you are abandoning the IdP. This does NOT affect grading_control_db.
docker exec -it grading-postgres-prod psql -U grading_owner -d postgres -c \
  "DROP DATABASE IF EXISTS zitadel; DROP ROLE IF EXISTS zitadel; DROP ROLE IF EXISTS zitadel_admin;"

# 9.4 (Optional) remove the nginx vhost (leaves grading/quiz vhosts intact).
sudo rm -f /etc/nginx/sites-enabled/auth.hrlab.uz
sudo nginx -t && sudo systemctl reload nginx
# (Optionally `sudo certbot delete --cert-name auth.hrlab.uz` to drop the cert.)

# 9.5 Revert grading-api to its previous IdP by restoring the old
#     GRADING_JWT_ISSUER_URI/_AUDIENCE in /opt/grading/.env.prod (and removing the
#     JWK_SET_URI line / compose edit), then: $C_grading up -d grading-api.
```

The IdP compose project is independent: there is **no path** by which `down`
removes a grading or quiz container, because (a) it is a separate compose project
file, (b) `grading-prod` is declared `external` so compose will not delete it, and
(c) quiz runs as a host process outside docker entirely.

---

## 10. Operational notes

- **Backups:** the ZITADEL `zitadel` database now lives in `grading-postgres`.
  Include it in the same backup routine, e.g. add to the pre-deploy/daily dump:
  ```sh
  docker exec grading-postgres-prod pg_dump -U grading_owner zitadel \
    | gzip > /opt/idp/backups/zitadel-$(date -u +%Y%m%dT%H%M%SZ).sql.gz
  ```
  **Also back up `ZITADEL_MASTERKEY`** (offline, in your secret vault) — without
  it the encrypted columns in the `zitadel` DB are unrecoverable.
- **Upgrades:** bump `ZITADEL_IMAGE` to a new pinned tag, re-run
  `--profile setup run --rm zitadel-setup` (applies DB migrations), then
  `up -d zitadel`.
- **Renewals:** certbot's systemd timer renews `auth.hrlab.uz` automatically,
  same as the other vhosts.
- **Logs:** `docker compose -f docker-compose.idp.yml logs -f zitadel`.
