import { useTranslation } from 'react-i18next';
import { LockedEntityHeader } from '@/shared/components/template-management/LockedEntityHeader';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { GradeStructure } from '../types';

interface LockedGradeStructureHeaderProps {
  structure: GradeStructure;
  onCreateNewVersion?: () => void;
}

/**
 * Read-only banner shown above APPROVED / LOCKED grade structures. Mirrors
 * LockedMethodologyHeader: "Approved on Y" + (when LOCKED) "Locked on W" + a
 * "Create new version" CTA gated on GRADE_EDIT.
 *
 * NOTE: the wire never carries `approved_by_name` / `locked_by_name`
 * (BE-1) — only opaque UUIDs. We therefore show a name-less fallback
 * (timestamp + "system" actor key) rather than rendering a raw UUID, and the
 * archive reason (audit-only) is not shown here. Thin wrapper around the
 * shared entity-agnostic `LockedEntityHeader`.
 */
export function LockedGradeStructureHeader({
  structure,
  onCreateNewVersion,
}: LockedGradeStructureHeaderProps) {
  const { t } = useTranslation();
  const isLocked = structure.status === 'LOCKED';

  const actor = t('common.unknown_actor');
  const timestamp = (isLocked ? structure.locked_at : structure.approved_at) ?? '—';
  const titleKey = isLocked
    ? 'gradeStructure.locked_header_title_locked'
    : 'gradeStructure.locked_header_title_approved';
  const bodyKey = isLocked
    ? 'gradeStructure.locked_header_body_locked'
    : 'gradeStructure.locked_header_body_approved';

  return (
    <LockedEntityHeader
      status={structure.status}
      testId="locked-grade-structure-header"
      title={t(titleKey)}
      body={t(bodyKey, { actor, timestamp })}
      approvedByLine={
        isLocked && structure.approved_at
          ? t('gradeStructure.locked_header_body_approved', {
              actor,
              timestamp: structure.approved_at,
            })
          : null
      }
      permission={PERMISSIONS.GRADE_EDIT}
      onCreateNewVersion={onCreateNewVersion}
      createNewVersionLabel={t('gradeStructure.create_new_version')}
      createNewVersionTestId="grade-structure-create-new-version"
    />
  );
}
