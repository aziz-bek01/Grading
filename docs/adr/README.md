# Architecture Decision Records (ADR)

This directory holds standalone Architecture Decision Records for
`grading.hrlab.uz`.

## Where decisions live

The original decisions **ADR-001 … ADR-012** are recorded inline in
[`архитектура.md`](../../архитектура.md) section 25 ("Architecture Decision
Records") and are referenced from the MVP blueprints in `docs/mvp1/`
(e.g. `02-security-blueprint.md`, `05-database-blueprint.md`). Those entries
stay where they are — they are the canonical numbering source.

Decisions made **after** the architecture document was frozen — ones that
change or relax an invariant established there — are recorded here as
individual files so the change has a reviewable, self-contained home. They
continue the same `ADR-NNN` numbering sequence (next free number = `013`).

## Format

Each ADR file is `ADR-NNN-short-slug.md` and follows the same lightweight shape
used in `архитектура.md` section 25, expanded with the sections a relaxation of
an existing invariant needs:

- **Status** — Proposed / Accepted / Superseded
- **Context** — the invariant in play and why a change was requested
- **Decision** — exact scope of what changes
- **Preservation mechanism** — how prior guarantees are kept where relaxed
- **Enforcement** — the gates that back the decision
- **Audit** — what is recorded
- **Consequences** — trade-offs accepted

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-013](ADR-013-super-admin-edit-approved-methodology-version.md) | Super-admin in-place edit of APPROVED methodology versions | Accepted |
