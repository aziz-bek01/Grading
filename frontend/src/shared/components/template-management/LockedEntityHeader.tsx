import { Lock, FilePlus2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import type { PermissionCode } from '@/shared/types/permissions';

export interface LockedEntityHeaderProps {
  /** Drives the root `data-status` attribute (e.g. structure.status / version.status). */
  status: string;
  /** Root `<section>` testid — kept caller-supplied since the two features never
   *  agreed on a shared prefix (`locked-grade-structure-header` vs
   *  `locked-methodology-header`). */
  testId: string;
  /** Fully resolved (translated) banner title. */
  title: string;
  /** Fully resolved (translated + interpolated) actor/timestamp line. The
   *  actor-resolution and timestamp-formatting rules genuinely differ per
   *  entity (see the grade-structure / methodology wrappers), so the caller
   *  bakes the final string before handing it to this shell. */
  body: string;
  /** Fully resolved secondary "Approved by ... on ..." line; omit to hide it. */
  approvedByLine?: string | null;
  /** Permission code(s) gating the "Create new version" CTA. */
  permission: PermissionCode | PermissionCode[];
  onCreateNewVersion?: () => void;
  createNewVersionLabel: string;
  createNewVersionTestId: string;
}

/**
 * Read-only banner shown above an APPROVED / LOCKED entity (grade structure or
 * methodology version): "Approved on Y" + (when LOCKED) "Locked on W" + a
 * "Create new version" CTA gated by the caller's permission code.
 *
 * Pure presentational shell — `PermissionGate` decides whether the CTA even
 * renders; backend remains the source of truth. All text is pre-resolved by
 * the caller so entity-specific i18n keys / actor-name-resolution /
 * timestamp-formatting rules stay exactly where they were.
 */
export function LockedEntityHeader({
  status,
  testId,
  title,
  body,
  approvedByLine,
  permission,
  onCreateNewVersion,
  createNewVersionLabel,
  createNewVersionTestId,
}: LockedEntityHeaderProps) {
  return (
    <section
      data-testid={testId}
      data-status={status}
      role="status"
      className="rounded-lg border border-locked/40 bg-locked-bg p-4 flex items-start gap-3"
    >
      <Lock size={18} className="text-locked mt-0.5" aria-hidden />
      <div className="flex-1 min-w-0">
        <h3 className="text-sm font-semibold text-text-primary inline-flex items-center gap-2">
          {title}
        </h3>
        <p className="text-xs text-text-secondary mt-1" data-testid="locked-actor-time">
          {body}
        </p>
        {approvedByLine ? (
          <p className="text-xs text-text-secondary mt-1" data-testid="locked-approved-by">
            {approvedByLine}
          </p>
        ) : null}
      </div>
      <PermissionGate permission={permission}>
        <Button
          variant="primary"
          size="sm"
          leadingIcon={<FilePlus2 size={14} />}
          onClick={onCreateNewVersion}
          data-testid={createNewVersionTestId}
        >
          {createNewVersionLabel}
        </Button>
      </PermissionGate>
    </section>
  );
}
