# ADR-013: Super-admin in-place edit of APPROVED methodology versions

Status: Accepted
Date: 2026-06-13
Supersedes (in part): the unconditional form of the §4 #2 / §15.6 methodology
immutability invariant for the **APPROVED** state only.
Related: ADR-002 (immutability gate), ADR-006 (security model), ADR-008 (audit),
changelog `tenant-schema/039–042`, seed `seeds/006`.

---

## Context

The architecture (§4 critical domain principle #2; §15.6 "the methodology
**version** is the unit of immutability") established that **an approved
methodology version is frozen forever** — any change must create a new version.
This invariant exists to keep evaluation scores **reproducible**: every
evaluation is anchored to the exact factors, weights and levels that were in
force when it was scored.

In practice the Product Owner accepted a controlled relaxation. Clients
repeatedly need to correct a scoring field (a factor weight, a level's points,
add a missing level, retire a level that should never have been selectable) on a
methodology that is already **APPROVED** and already has evaluations pinned to
it — without forcing a full new-version migration of every in-flight project.
The cost of "new version for every typo" was driving users to delete-and-recreate
workflows that are worse for auditability than a narrow, audited in-place edit.

The decision is therefore to **relax the immutability invariant for APPROVED
versions only, for scoring fields only, for super admins only**, while keeping
reproducibility of already-scored evaluations intact by construction.

## Decision

Permit an in-place edit of an **APPROVED** methodology version, bounded as
follows:

- **Editable scope:** factor scoring fields (weight, max points, required,
  sort order, i18n labels) and factor-level scoring fields (points, scale value,
  order, i18n), plus add-factor / add-level and remove (see preservation).
- **Excluded:** `scoring_mode` is **not** editable on an APPROVED version —
  changing the scoring algorithm would alter the meaning of every stored score
  and cannot be reconciled with frozen snapshots. It remains version-level and
  immutable post-approval.
- **State scope:** **APPROVED only.** **LOCKED and ARCHIVED stay
  hard-immutable** — no permission and no flag can mutate their factors or
  levels. The carve-out never weakens them.
- **Actor scope:** only a holder of `METHODOLOGY_EDIT_APPROVED`. A plain
  `METHODOLOGY_EDIT` holder is still rejected on APPROVED.

## Preservation mechanism

Reproducibility of already-scored evaluations is preserved **by construction**,
not by recompute:

- **Per-evaluation immutable basis snapshot (if-absent).** On first
  transition/score, each evaluation captures a `methodology_basis_snapshot`
  (changelog 039/040). `setMethodologyBasisSnapshotIfAbsent` writes once and
  **never overwrites** — a later approved edit cannot retro-change a frozen
  basis.
- **Soft-deprecate instead of delete.** A factor / level that is *referenced* by
  any evaluation is **soft-deprecated** (`deprecated_at` / `deprecated_by`)
  rather than removed, so historical scores keep a live FK target. Unreferenced
  rows are hard-deleted.
- **RESTRICT FKs.** `evaluation_scores` → `factor_levels` / `factors` are
  `ON DELETE RESTRICT`, so an edit can never orphan a stored score.
- **Recompute is unreachable from methodology writes.** Factor / level writes
  do **not** trigger evaluation recompute. A pre-existing evaluation's
  `raw_total_score`, `displayed_total_score` and per-factor `raw_factor_score`
  remain **byte-for-byte unchanged** after an approved edit.

## Enforcement

`METHODOLOGY_EDIT_APPROVED` is a **super-admin-only** permission. The seed-006
invariant grants it exclusively to `HRLAB_SUPER_ADMIN`; no client role may hold
it. The edit is then defended by **three independent gates**, any one of which
blocks an unauthorised write (defence-in-depth):

1. **`@PreAuthorize`** on the controller endpoints (coarse permission check).
2. **Policy** —
   `MethodologyVersionImmutabilityPolicy.ensureMutableOrApprovedEdit(status,
   canEditApproved)`: DRAFT → mutable; APPROVED → mutable **only** with
   `METHODOLOGY_EDIT_APPROVED`; LOCKED/ARCHIVED → always rejected. The
   tenant-scoped load (`findByIdAndTenantId`) runs **before** this branch, so a
   cross-tenant object id fails with `TenantAccessDeniedException` and never
   reaches the approved path.
3. **GUC-gated DB trigger** — `prevent_locked_factor_changes` /
   `prevent_locked_factor_level_changes` (changelog 042) `RAISE 23514` on any
   write to an APPROVED version's factors/levels **unless** the transaction-local
   GUC `app.methodology_approved_edit = 'on'` is set. The service sets it with
   `SET LOCAL` **only** on the audited approved-edit branch; `SET LOCAL` resets
   at commit/rollback so it cannot leak across pooled connections. LOCKED /
   ARCHIVED `RAISE` regardless of the GUC.

## Audit

Every approved edit is fully recorded:

- The normal **per-field** events (`FACTOR_UPDATED`, `FACTOR_LEVEL_UPDATED`,
  `FACTOR_DEPRECATED`, `FACTOR_LEVEL_DEPRECATED`, …) are emitted as usual.
- Plus one **`METHODOLOGY_APPROVED_EDIT` umbrella** row per approved-edit
  write-operation/transaction (see `ApprovedEditAudit`), framing the change as an
  approved edit and carrying the blast radius `frozenEvaluationCount`
  (= non-archived evaluations pinned to the version at edit time).

## Consequences

- **Reproducibility is weakened-but-recorded.** The methodology version is no
  longer a literally frozen object; its *current* factors may differ from what an
  old evaluation was scored against. That history is fully recoverable from the
  per-evaluation basis snapshot + the per-field and umbrella audit trail, and
  no stored score changes — but consumers must read scores against the snapshot,
  not the live version, when reproducing a historical evaluation.
- **Higher trust placed in the super-admin role.** The relaxation concentrates
  on `METHODOLOGY_EDIT_APPROVED`; the seed-006 single-role invariant and the
  three gates are the controls keeping that trust bounded.
- **LOCKED remains the true "freeze".** Clients who need a hard, uneditable
  methodology should LOCK it; APPROVED is now an editable-by-super-admin state.
