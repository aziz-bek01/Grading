import { useTranslation } from 'react-i18next';
import { LockedEntityHeader } from '@/shared/components/template-management/LockedEntityHeader';
import { PERMISSIONS } from '@/shared/types/permissions';
import { formatDateSafe } from '@/shared/lib/dates';
import type { MethodologyVersion } from '../types';

interface LockedMethodologyHeaderProps {
  version: MethodologyVersion;
  onCreateNewVersion?: () => void;
}

/**
 * Read-only banner shown above approved / locked methodology versions.
 * Per design-foundation §13: "Locked by [actor] on [timestamp]" + CTA.
 *
 * Pure UI — `PermissionGate` (inside the shared `LockedEntityHeader`) decides
 * whether the "Create new version" CTA even renders; backend remains source
 * of truth. Thin wrapper around the shared entity-agnostic `LockedEntityHeader`.
 */
export function LockedMethodologyHeader({
  version,
  onCreateNewVersion,
}: LockedMethodologyHeaderProps) {
  const { t, i18n } = useTranslation();
  const isLocked = version.status === 'LOCKED';

  // FE-017: reuse the single shared date formatter (`formatDateSafe`) instead
  // of hand-rolling `new Date(iso).toLocaleString(i18n.language)` — it also
  // guards the null/invalid-date path uniformly across the app.
  const formatTs = (iso?: string | null) => formatDateSafe(iso, i18n.language);

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
    <LockedEntityHeader
      status={version.status}
      testId="locked-methodology-header"
      title={t(titleKey)}
      body={t(bodyKey, { actor, timestamp: formatTs(timestamp) })}
      /*
       * D-407 — if both APPROVED + LOCKED metadata are present, show the
       * approval line separately so the user sees "Approved by X on Y" in
       * addition to the locked banner.
       */
      approvedByLine={
        isLocked && version.approved_by_name && version.approved_at
          ? t('methodology.locked_header_body_approved', {
              actor: version.approved_by_name,
              timestamp: formatTs(version.approved_at),
            })
          : null
      }
      permission={PERMISSIONS.METHODOLOGY_EDIT}
      onCreateNewVersion={onCreateNewVersion}
      createNewVersionLabel={t('methodology.create_new_version')}
      createNewVersionTestId="locked-create-new-version"
    />
  );
}
