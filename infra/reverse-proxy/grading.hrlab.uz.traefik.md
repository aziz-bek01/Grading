# grading.hrlab.uz — Traefik routing (variant of the host-nginx vhost)

Use this **only if the VPS already fronts its other subdomains with Traefik**.
It achieves the same same-origin split as
`infra/reverse-proxy/grading.hrlab.uz.nginx.conf`:

| Path                                   | Service          | Container target     |
| -------------------------------------- | ---------------- | -------------------- |
| `/api/*`, `/actuator/health`, docs     | grading-api      | `127.0.0.1:18080`    |
| everything else                        | grading-frontend | `127.0.0.1:18081`    |

## Coexistence rules

- These routers/services are uniquely named `grading-*`. They do **not** touch
  any other subdomain's Traefik config.
- The grading containers stay bound to `127.0.0.1` (as in
  `docker-compose.prod.yml`). Traefik reaches them via the host loopback. Do
  **not** attach Traefik to the `grading-prod` network unless you deliberately
  switch to docker-provider routing (see option B).

---

## Option A — File provider (containers stay on 127.0.0.1, recommended)

Keeps the compose file unchanged. Add this to Traefik's dynamic file provider
(e.g. `/etc/traefik/dynamic/grading.yml`). Traefik must have an entrypoint
`websecure` on `:443` and a cert resolver (e.g. `letsencrypt`) already set up
for the other subdomains.

```yaml
http:
  routers:
    grading-api:
      rule: "Host(`grading.hrlab.uz`) && (PathPrefix(`/api`) || PathPrefix(`/actuator/health`) || PathPrefix(`/v3/api-docs`) || PathPrefix(`/swagger-ui`))"
      entryPoints: ["websecure"]
      service: grading-api
      priority: 100          # higher than the SPA catch-all
      tls:
        certResolver: letsencrypt
    grading-frontend:
      rule: "Host(`grading.hrlab.uz`)"
      entryPoints: ["websecure"]
      service: grading-frontend
      priority: 1            # catch-all fallback
      tls:
        certResolver: letsencrypt

  services:
    grading-api:
      loadBalancer:
        servers:
          - url: "http://127.0.0.1:18080"
        passHostHeader: true
    grading-frontend:
      loadBalancer:
        servers:
          - url: "http://127.0.0.1:18081"
        passHostHeader: true
```

HTTP->HTTPS redirect: rely on the global Traefik redirect the other subdomains
already use (an `entryPoint.web.http.redirections` block). Do not redefine it
here.

---

## Option B — Docker provider (labels on the compose services)

Only if the VPS Traefik uses the docker provider AND you are willing to attach
Traefik to the grading network. In that case **remove the `127.0.0.1:18080` /
`18081` `ports:` mappings** from `docker-compose.prod.yml` (Traefik reaches the
containers over the shared docker network instead) and attach Traefik's network.

Labels to add under each service in a compose override (do **not** edit the base
prod compose; use `docker-compose.prod.traefik.yml` as an override):

```yaml
services:
  grading-api:
    networks: [grading-prod, traefik]   # traefik = the existing Traefik network
    labels:
      - "traefik.enable=true"
      - "traefik.docker.network=traefik"
      - "traefik.http.routers.grading-api.rule=Host(`grading.hrlab.uz`) && (PathPrefix(`/api`) || PathPrefix(`/actuator/health`) || PathPrefix(`/v3/api-docs`) || PathPrefix(`/swagger-ui`))"
      - "traefik.http.routers.grading-api.entrypoints=websecure"
      - "traefik.http.routers.grading-api.priority=100"
      - "traefik.http.routers.grading-api.tls.certresolver=letsencrypt"
      - "traefik.http.services.grading-api.loadbalancer.server.port=8080"
  grading-frontend:
    networks: [grading-prod, traefik]
    labels:
      - "traefik.enable=true"
      - "traefik.docker.network=traefik"
      - "traefik.http.routers.grading-frontend.rule=Host(`grading.hrlab.uz`)"
      - "traefik.http.routers.grading-frontend.entrypoints=websecure"
      - "traefik.http.routers.grading-frontend.priority=1"
      - "traefik.http.routers.grading-frontend.tls.certresolver=letsencrypt"
      - "traefik.http.services.grading-frontend.loadbalancer.server.port=8080"

networks:
  traefik:
    external: true
```

> Prefer **Option A** for the shared VPS — it keeps grading containers bound to
> localhost and requires zero changes to the base prod compose, minimising any
> chance of disturbing the other subdomains.
