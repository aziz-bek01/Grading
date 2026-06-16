# Observability — grading.hrlab.uz (Batch 6)

Business + worker metrics and Prometheus alert rules for the grading API.

## Scrape endpoint

Metrics are exposed at `GET /actuator/prometheus`.

**This endpoint is NOT public.** It requires authentication / internal-network
access — it is deliberately omitted from the anonymous allowlist in
`backend/.../security/SecurityConfig.java` (only `/actuator/health/**` and
`/actuator/info` are public). Metrics can leak tenant counts and operational
data, so a scraper must reach it over the cluster network or with a bearer
token. The Helm chart already annotates the pod for in-cluster scraping
(`infra/helm/grading-api/templates/deployment.yaml`: `prometheus.io/path:
/actuator/prometheus`).

## Metric inventory

Emitted by `uz.hrlab.grading.common.metrics.WorkerMetrics`. Tagged by
`type` / `outcome` ONLY — never by tenant_id / user_id / project_id / job id, and
never with salary values or file contents (tenant-isolation + salary gates).

| Metric | Type | Tags | Incremented at |
| --- | --- | --- | --- |
| `grading_worker_outcome_total` | counter | `type` (export\|import\|report), `outcome` (started\|succeeded\|failed\|dead_letter\|retry_dispatched) | Export/Import/Report worker transition points + WorkerReQueuer dispatch |
| `grading_worker_dead_letter_current` | gauge | (none) | Bumped on every DEAD_LETTER transition; the `> 0` alert source |
| `grading_worker_generation_seconds` | timer | `type` | Wraps the generation/processing body of each worker |

Built-in Micrometer/Actuator series also used: `up{job="grading-api"}` (scrape
liveness) and `logback_events_total{level="error"}` (error-log spike / coarse
audit-failure signal).

## Alert rules

`alerts/grading-alerts.yml` — self-contained Prometheus rule groups:

- `GradingWorkerDeadLetterPresent` / `GradingWorkerDeadLetterRate` — dead-letter present.
- `GradingWorkerFailureRateHigh` — worker failure-rate spike.
- `GradingWorkerGenerationSlow` — generation p95 latency.
- `GradingErrorLogSpike` — error-log / audit-failure signal.
- `GradingApiDown` — API down / unreachable health.

Drop the file into your Prometheus `rule_files:` (or a `PrometheusRule` CR) and
route the `team: hrlab-grading` label to Alertmanager. No live Alertmanager is
wired here.
