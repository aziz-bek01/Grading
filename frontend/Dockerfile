# syntax=docker/dockerfile:1.7
# grading-frontend — Vite + React 18 + TypeScript static bundle, served by nginx.
# Multi-stage: node builder → nginx-unprivileged runtime (non-root, port 8080).
# No secrets in the image (runtime config served from /config.json populated
# by a K8s ConfigMap — see infra/helm/grading-frontend).

# -----------------------------------------------------------------------------
# Stage 1 — Build the SPA bundle.
# -----------------------------------------------------------------------------
# Node 22 (>= 22.12): Vite 8 is rolldown-based and refuses to build on Node
# < 20.19 / < 22.12 ("Vite requires Node.js version 20.19+ or 22.12+").
FROM node:22-alpine AS builder

WORKDIR /workspace

# Install deps first — best cache layer.
# NOTE: `npm ci` can silently skip platform-specific OPTIONAL native binaries
# when package-lock.json was generated on another OS (npm/cli#4828). That makes
# rolldown's native binding (@rolldown/binding-linux-x64-musl on alpine) absent
# at build time, so `vite build` dies with "Cannot find native binding".
# `npm install` re-evaluates the os/cpu/libc applicability of optionalDependencies
# for the running platform and installs the correct musl binding from the lockfile.
COPY package.json package-lock.json ./
RUN npm install --no-audit --no-fund

# Build. NOTE: .env.production MUST be copied so `vite build` (mode=production)
# picks up VITE_DEV_AUTH=false / VITE_USE_MSW=false — otherwise the dev login
# (passwordless "Sign in as ...") ships to prod.
COPY tsconfig*.json vite.config.ts vitest.config.ts postcss.config.js tailwind.config.ts eslint.config.js index.html .env.production ./
COPY public/ public/
COPY src/ src/
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2 — Serve via nginx-unprivileged (non-root, port 8080).
# nginxinc/nginx-unprivileged ships nginx running as uid 101 with all writable
# paths configured for read-only-rootfs K8s pods.
# -----------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.27.2-alpine AS runtime

ARG IMAGE_VERSION=dev
ARG IMAGE_REVISION=unknown
ARG IMAGE_CREATED=unknown
LABEL org.opencontainers.image.title="grading-frontend" \
      org.opencontainers.image.description="grading.hrlab.uz React SPA served by nginx" \
      org.opencontainers.image.vendor="HR Laboratories" \
      org.opencontainers.image.source="https://github.com/hrlab-uz/grading" \
      org.opencontainers.image.licenses="Proprietary" \
      org.opencontainers.image.version="${IMAGE_VERSION}" \
      org.opencontainers.image.revision="${IMAGE_REVISION}" \
      org.opencontainers.image.created="${IMAGE_CREATED}"

# Strip the default config and ship ours (secure headers, SPA fallback).
USER root
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/grading.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html

# Default runtime config — overridden at deploy time by ConfigMap-mounted file.
# (Frontend reads /config.json on bootstrap to pick API base URL, OIDC issuer,
# feature flags etc. — NEVER bake secrets here.)
RUN printf '{ "apiBaseUrl": "/api", "environment": "unknown" }\n' > /usr/share/nginx/html/config.json \
 && chown -R 101:101 /usr/share/nginx/html

USER 101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --spider --tries=1 http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
