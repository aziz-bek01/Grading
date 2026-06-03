#!/usr/bin/env bash
# =============================================================================
# grading.hrlab.uz — FIRST-TIME VPS bootstrap (run ONCE as root).
# =============================================================================
# Prepares a SHARED VPS (that already serves quiz.hrlab.uz via host nginx) to
# run the grading stack. It is IDEMPOTENT and is engineered to NEVER disturb the
# existing sites:
#   * Docker is installed from the official apt repo (adds packages only).
#   * A dedicated `grading` deploy user (home /opt/grading, docker group) is
#     created for the GitHub Actions CD to SSH into — NOT root.
#   * A SEPARATE nginx vhost is added for grading.hrlab.uz; `nginx -t` is run
#     BEFORE every reload, and on failure the new vhost is removed so the
#     running quiz.hrlab.uz vhost is never affected.
#   * TLS is obtained with `certbot --nginx` (the same managed pattern already
#     used by quiz.hrlab.uz on this box).
#
# Inputs (environment):
#   GRADING_CD_PUBKEY   the CD SSH PUBLIC key to authorize for the deploy user
#   CERTBOT_EMAIL       Let's Encrypt account email (default: az.asqarov@gmail.com)
#
# Usage (from the dir holding this script + grading.hrlab.uz.http.conf):
#   GRADING_CD_PUBKEY="ssh-ed25519 AAAA... grading-cd@github-actions" \
#   CERTBOT_EMAIL="you@example.com" bash bootstrap.sh
# =============================================================================
set -euo pipefail

DOMAIN="grading.hrlab.uz"
WWW="www.grading.hrlab.uz"
DEPLOY_USER="grading"
APP_DIR="/opt/grading"
HERE="$(cd "$(dirname "$0")" && pwd)"

log() { printf '\n== %s ==\n' "$*"; }

# ---------------------------------------------------------------------------
# 1) Docker CE + compose plugin (official repo). Adds packages only.
# ---------------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  log "Installing Docker CE + compose plugin"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  if [ ! -f /etc/apt/keyrings/docker.asc ]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
  fi
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
else
  log "Docker already installed: $(docker --version)"
fi

# ---------------------------------------------------------------------------
# 2) Dedicated deploy user + app dir (idempotent).
# ---------------------------------------------------------------------------
if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  log "Creating deploy user '$DEPLOY_USER' (home $APP_DIR)"
  useradd --system --create-home --home-dir "$APP_DIR" --shell /bin/bash "$DEPLOY_USER"
else
  log "Deploy user '$DEPLOY_USER' already exists"
fi
usermod -aG docker "$DEPLOY_USER"
mkdir -p "$APP_DIR/infra/db/prod" "$APP_DIR/infra/frontend"
chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"

# ---------------------------------------------------------------------------
# 3) Authorize the CD public key for the deploy user (idempotent).
# ---------------------------------------------------------------------------
if [ -n "${GRADING_CD_PUBKEY:-}" ]; then
  log "Authorizing CD public key for '$DEPLOY_USER'"
  install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR/.ssh"
  touch "$APP_DIR/.ssh/authorized_keys"
  grep -qxF "$GRADING_CD_PUBKEY" "$APP_DIR/.ssh/authorized_keys" \
    || echo "$GRADING_CD_PUBKEY" >> "$APP_DIR/.ssh/authorized_keys"
  chmod 600 "$APP_DIR/.ssh/authorized_keys"
  chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR/.ssh"
else
  log "GRADING_CD_PUBKEY not set — skipping CD key install (set it later)"
fi

# ---------------------------------------------------------------------------
# 4) Add the grading nginx vhost (HTTP). Never touches other vhosts.
#    Validate with `nginx -t` BEFORE reload; roll back on failure.
# ---------------------------------------------------------------------------
log "Installing nginx vhost for $DOMAIN"
cp -f "$HERE/grading.hrlab.uz.http.conf" /etc/nginx/sites-available/grading.hrlab.uz
ln -sf ../sites-available/grading.hrlab.uz /etc/nginx/sites-enabled/grading.hrlab.uz
if nginx -t; then
  systemctl reload nginx
  log "nginx reloaded OK (quiz.hrlab.uz unaffected)"
else
  echo "FATAL: nginx -t failed — removing grading vhost to protect existing sites" >&2
  rm -f /etc/nginx/sites-enabled/grading.hrlab.uz
  nginx -t && systemctl reload nginx || true
  exit 1
fi

# ---------------------------------------------------------------------------
# 5) TLS via certbot --nginx (managed; idempotent). Edits ONLY the grading
#    vhost (server_name match), adds the 443 block + HTTP->HTTPS redirect.
# ---------------------------------------------------------------------------
if certbot certificates 2>/dev/null | grep -q "Domains:.*\b${DOMAIN}\b"; then
  log "TLS cert already present for $DOMAIN — skipping issuance"
else
  log "Obtaining TLS cert for $DOMAIN + $WWW"
  certbot --nginx -d "$DOMAIN" -d "$WWW" \
    --non-interactive --agree-tos -m "${CERTBOT_EMAIL:-az.asqarov@gmail.com}" --redirect
fi
nginx -t && systemctl reload nginx

log "Bootstrap complete"
docker --version
docker compose version
echo "Deploy user: $DEPLOY_USER   App dir: $APP_DIR"
