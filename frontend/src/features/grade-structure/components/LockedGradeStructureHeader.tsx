import { useTranslation } from 'react-i18next';
import { Lock, FilePlus2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { GradeStructure } from '../types';

interface LockedGradeStructureHeaderProps {
  structure: GradeStructure;
  onCreateNewVersion?: () => void;
}

/**
 * Read-only banner shown above APPROVED / LOCKED grade structures. Mirrors
 * LockedMethodologyHeader: "Approved by X on Y" + (when LOCKED) "Locked by
 * Z on W" + a "Create new version" CTA gated on GRADE_EDIT.
 */
export function LockedGradeStructureHeader({
  structure,
  onCreateNewVersion,
}: LockedGradeStructureHeaderProps) {
  const { t } = useTranslation();
  const isLocked = structure.status === 'LOCKED';

  const actor = isLocked
    ? structure.locked_by_name ?? structure.locked_by ?? t('common.unknown_actor')
    : structure.approved_by_name ?? structure.approved_by ?? t('common.unknown_actor');
  const timestamp = isLocked ? structure.locked_at : structure.approved_at;
  const titleKey = isLocked
    ? 'gradeStructure.locked_header_title_locked'
    : 'gradeStructure.locked_header_title_approved';
  const bodyKey = isLocked
    ? 'gradeStructure.locked_header_body_locked'
    : 'gradeStructure.locked_header_body_approved';

  return (
    <section
      data-testid="locked-grade-structure-header"
      data-status={structure.status}
      role="status"
      className="rounded-lg border border-locked/40 bg-locked-bg p-4 flex items-start gap-3"
    >
      <Lock size={18} className="text-locked mt-0.5" aria-hidden />
      <div className="flex-1 min-w-0">
        <h3 className="text-sm font-semibold text-text-primary inline-flex items-center gap-2">
          {t(titleKey)}
        </h3>
        <p className="text-xs text-text-secondary mt-1" data-testid="locked-actor-time">
          {t(bodyKey, { actor, timestamp: timestamp ?? '—' })}
        </p>
        {isLocked && structure.approved_by_name && structure.approved_at && (
          <p
            className="text-xs text-text-secondary mt-1"
            data-testid="locked-approved-by"
          >
            {t('gradeStructure.locked_header_body_approved', {
              actor: structure.approved_by_name,
              timestamp: structure.approved_at,
            })}
          </p>
        )}
      </div>
      <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
        <Button
          variant="primary"
          size="sm"
          leadingIcon={<FilePlus2 size={14} />}
          onClick={onCreateNewVersion}
          data-testid="grade-structure-create-new-version"
        >
          {t('gradeStructure.create_new_version')}
        </Button>
      </PermissionGate>
    </section>
  );
}
