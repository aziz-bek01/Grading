import { useTranslation } from 'react-i18next';
import { Lock, FilePlus2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { MethodologyVersion } from '../types';

interface LockedMethodologyHeaderProps {
  version: MethodologyVersion;
  onCreateNewVersion?: () => void;
}

/**
 * Read-only banner shown above approved / locked methodology versions.
 * Per design-foundation §13: "Locked by [actor] on [timestamp]" + CTA.
 *
 * Pure UI — `PermissionGate` decides whether the "Create new version" CTA
 * even renders; backend remains source of truth.
 */
export function LockedMethodologyHeader({
  version,
  onCreateNewVersion,
}: LockedMethodologyHeaderProps) {
  const { t, i18n } = useTranslation();
  const isLocked = version.status === 'LOCKED';

  // Reuse the codebase's established locale-aware timestamp pattern
  // (`new Date(iso).toLocaleString(i18n.language)`, as in AuditListPage /
  // ApprovalRequestCard / StageStatusCard) instead of printing the raw ISO.
  const formatTs = (iso?: string | null) =>
    iso ? new Date(iso).toLocaleString(i18n.language) : '—';

  /**
   * D-407 / PC4-5 — display the human-readable name resolved server-side.
   * Fall back to the UUID only if the backend hasn't shipped the join yet
   * (transitional). If both are missing, show `common.unknown_actor`.
   */
  const actor = isLocked
    ? version.locked_by_name ?? version.locked_by ?? t('common.unknown_actor')
    : version.approved_by_name ?? version.approved_by ?? t('common.unknown_actor');
  const timestamp = isLocked ? version.locked_at : version.approved_at;
  const titleKey = isLocked
    ? 'methodology.locked_header_title_locked'
    : 'methodology.locked_header_title_approved';
  const bodyKey = isLocked
    ? 'methodology.locked_header_body_locked'
    : 'methodology.locked_header_body_approved';

  return (
    <section
      data-testid="locked-methodology-header"
      data-status={version.status}
      role="status"
      className="rounded-lg border border-locked/40 bg-locked-bg p-4 flex items-start gap-3"
    >
      <Lock size={18} className="text-locked mt-0.5" aria-hidden />
      <div className="flex-1 min-w-0">
        <h3 className="text-sm font-semibold text-text-primary inline-flex items-center gap-2">
          {t(titleKey)}
        </h3>
        <p className="text-xs text-text-secondary mt-1" data-testid="locked-actor-time">
          {t(bodyKey, {
            actor,
            timestamp: formatTs(timestamp),
          })}
        </p>
        {/*
          D-407 — if both APPROVED + LOCKED metadata are present, show the
          approval line separately so the user sees "Approved by X on Y" in
          addition to the locked banner.
        */}
        {isLocked && version.approved_by_name && version.approved_at && (
          <p
            className="text-xs text-text-secondary mt-1"
            data-testid="locked-approved-by"
          >
            {t('methodology.locked_header_body_approved', {
              actor: version.approved_by_name,
              timestamp: formatTs(version.approved_at),
            })}
          </p>
        )}
      </div>
      <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
        <Button
          variant="primary"
          size="sm"
          leadingIcon={<FilePlus2 size={14} />}
          onClick={onCreateNewVersion}
          data-testid="locked-create-new-version"
        >
          {t('methodology.create_new_version')}
        </Button>
      </PermissionGate>
    </section>
  );
}
