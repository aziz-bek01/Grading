import { useTranslation } from 'react-i18next';
import { Languages, Check, Archive, BookmarkPlus, Pencil } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { Locale } from '@/shared/types/common';
import { MethodologyStatusBadge } from '../components/MethodologyStatusBadge';
import { MethodologyTypeBadge } from '../components/MethodologyTypeBadge';
import { ScoringModeBadge } from '../components/ScoringModeBadge';
import type { Methodology, MethodologyVersion } from '../types';

interface MethodologyBuilderHeaderProps {
  methodology: Methodology;
  version: MethodologyVersion;
  currentLocale: Locale;
  onOpenTranslations: () => void;
  onSaveAsTemplate: () => void;
  onEditMetadata: () => void;
  onApprove: () => void;
  onArchive: () => void;
}

/**
 * Builder page title bar: name + status/type/scoring badges, plus the
 * translations / save-as-template / edit-metadata / approve / archive
 * actions (each gated by `PermissionGate`; the lifecycle trio is DRAFT-only).
 * Extracted from `MethodologyBuilderPage` (FE-041); unchanged behaviour and
 * testids.
 */
export function MethodologyBuilderHeader({
  methodology,
  version,
  currentLocale,
  onOpenTranslations,
  onSaveAsTemplate,
  onEditMetadata,
  onApprove,
  onArchive,
}: MethodologyBuilderHeaderProps) {
  const { t } = useTranslation();

  return (
    <header className="flex items-start justify-between gap-4 flex-wrap" data-testid="methodology-header">
      <div>
        <div className="flex items-center gap-2 flex-wrap">
          <h1 className="text-2xl text-text-primary">
            {methodology.name_i18n?.[currentLocale] ?? methodology.code}
          </h1>
          <MethodologyStatusBadge status={version.status} />
          <MethodologyTypeBadge type={methodology.methodology_type} />
          <ScoringModeBadge mode={version.scoring_mode} />
        </div>
        <p className="text-sm text-text-secondary mt-1">
          {t('methodology.version_label', { number: version.version_number })}
        </p>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <PermissionGate permission={PERMISSIONS.METHODOLOGY_READ}>
          <Button
            variant="secondary"
            size="sm"
            leadingIcon={<Languages size={14} />}
            onClick={onOpenTranslations}
            data-testid="open-translations"
          >
            {t('methodology.translations_button')}
          </Button>
        </PermissionGate>

        <PermissionGate permission={PERMISSIONS.METHODOLOGY_CREATE}>
          <Button
            variant="secondary"
            size="sm"
            leadingIcon={<BookmarkPlus size={14} />}
            onClick={onSaveAsTemplate}
            data-testid="action-save-as-template"
          >
            {t('methodology.save_as_template.action')}
          </Button>
        </PermissionGate>

        {/* Lifecycle actions (edit metadata / approve / archive) stay DRAFT-only.
            Approved-edit mode (FE-1) deliberately exposes ONLY factor/level
            scoring edits — scoring_mode/type metadata and the approve/archive
            transitions remain DRAFT-bound (backend enforces the same). */}
        {version.status === 'DRAFT' ? (
          <>
            <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<Pencil size={14} />}
                onClick={onEditMetadata}
                data-testid="action-edit-metadata"
              >
                {t('methodology.metadata.edit_action')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.METHODOLOGY_APPROVE}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Check size={14} />}
                onClick={onApprove}
                data-testid="action-approve"
              >
                {t('methodology.approve_lock')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
              <Button
                variant="ghost"
                size="sm"
                leadingIcon={<Archive size={14} />}
                onClick={onArchive}
                data-testid="action-archive"
              >
                {t('common.archive')}
              </Button>
            </PermissionGate>
          </>
        ) : null}
      </div>
    </header>
  );
}
