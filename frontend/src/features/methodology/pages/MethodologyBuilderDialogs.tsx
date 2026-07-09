import { useTranslation } from 'react-i18next';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { FactorEditor } from '../components/FactorEditor';
import { SaveAsTemplateDrawer } from '../components/SaveAsTemplateDrawer';
import {
  MethodologyMetadataDrawer,
  type MethodologyMetadataPatch,
} from '../components/MethodologyMetadataDrawer';
import type { Factor, FactorLevel, Methodology, MethodologyVersion, SaveAsTemplatePayload } from '../types';

interface MethodologyBuilderDialogsProps {
  methodology: Methodology;
  version: MethodologyVersion;
  readOnly: boolean;

  // Factor/level editor drawer
  editorOpen: boolean;
  editorFactor: Factor | null;
  onCloseEditor: () => void;
  onFactorSubmit: (patch: {
    code: string;
    name_i18n: import('@/shared/types/common').LocalizedString;
    description_i18n?: import('@/shared/types/common').LocalizedString;
    weight: number;
    max_points: number;
    required: boolean;
  }) => Promise<void>;
  onAddLevel: (next: Omit<FactorLevel, 'id' | 'factor_id'>) => Promise<void>;
  onUpdateLevel: (lvl: FactorLevel) => Promise<void>;
  onRemoveLevel: (lvl: FactorLevel) => void;
  onReorderLevel: (lvl: FactorLevel, direction: 'up' | 'down') => Promise<void>;

  // Approve version
  approveOpen: boolean;
  onApproveCancel: () => void;
  onApproveConfirm: () => Promise<void>;

  // Remove-factor / remove-level confirmation (FE-049)
  removeFactorTargetOpen: boolean;
  onRemoveFactorCancel: () => void;
  onRemoveFactorConfirm: () => Promise<void>;
  removeLevelTargetOpen: boolean;
  onRemoveLevelCancel: () => void;
  onRemoveLevelConfirm: () => Promise<void>;

  // Archive version (reason required)
  archiveOpen: boolean;
  onArchiveCancel: () => void;
  onArchiveConfirm: (reason: string) => Promise<void>;

  // Approved-edit first-action confirm gate (FE-1)
  approvedEditConfirmOpen: boolean;
  onApprovedEditCancel: () => void;
  onApprovedEditConfirm: () => void;

  // Create new version
  newVersionOpen: boolean;
  onNewVersionCancel: () => void;
  onNewVersionConfirm: () => Promise<void>;

  // Save as template (Epic E)
  saveTemplateOpen: boolean;
  onSaveTemplateClose: () => void;
  onSaveTemplateSubmit: (payload: SaveAsTemplatePayload) => Promise<void>;

  // Edit methodology + version metadata
  metadataOpen: boolean;
  onMetadataClose: () => void;
  onMetadataSubmit: (patch: MethodologyMetadataPatch) => Promise<void>;
}

/**
 * Every modal/drawer/confirm surface the builder page can open: the
 * factor/level editor drawer, the approve/archive/new-version/remove-factor/
 * remove-level confirmations, the approved-edit first-action gate, the
 * save-as-template drawer and the metadata-edit drawer. Extracted from
 * `MethodologyBuilderPage` (FE-041) as one cohesive "dialogs" composition —
 * unchanged behaviour and testids.
 */
export function MethodologyBuilderDialogs({
  methodology,
  version,
  readOnly,
  editorOpen,
  editorFactor,
  onCloseEditor,
  onFactorSubmit,
  onAddLevel,
  onUpdateLevel,
  onRemoveLevel,
  onReorderLevel,
  approveOpen,
  onApproveCancel,
  onApproveConfirm,
  removeFactorTargetOpen,
  onRemoveFactorCancel,
  onRemoveFactorConfirm,
  removeLevelTargetOpen,
  onRemoveLevelCancel,
  onRemoveLevelConfirm,
  archiveOpen,
  onArchiveCancel,
  onArchiveConfirm,
  approvedEditConfirmOpen,
  onApprovedEditCancel,
  onApprovedEditConfirm,
  newVersionOpen,
  onNewVersionCancel,
  onNewVersionConfirm,
  saveTemplateOpen,
  onSaveTemplateClose,
  onSaveTemplateSubmit,
  metadataOpen,
  onMetadataClose,
  onMetadataSubmit,
}: MethodologyBuilderDialogsProps) {
  const { t } = useTranslation();

  return (
    <>
      <FactorEditor
        open={editorOpen}
        factor={editorFactor}
        scoringMode={version.scoring_mode}
        readOnly={readOnly}
        onClose={onCloseEditor}
        onSubmit={onFactorSubmit}
        onAddLevel={onAddLevel}
        onUpdateLevel={onUpdateLevel}
        onRemoveLevel={onRemoveLevel}
        onReorderLevel={onReorderLevel}
      />

      <ConfirmDialog
        open={approveOpen}
        title={t('methodology.confirm.approve_title')}
        body={t('methodology.confirm.approve_body')}
        confirmLabel={t('methodology.approve_lock')}
        onCancel={onApproveCancel}
        onConfirm={onApproveConfirm}
      />

      {/* FE-049 — remove-factor / remove-level confirmation (replaces window.confirm
          with the shared ConfirmDialog). */}
      <ConfirmDialog
        open={removeFactorTargetOpen}
        destructive
        title={t('methodology.confirm_remove_factor')}
        body={t('methodology.confirm.remove_factor_body')}
        confirmLabel={t('common.delete')}
        onCancel={onRemoveFactorCancel}
        onConfirm={onRemoveFactorConfirm}
      />

      <ConfirmDialog
        open={removeLevelTargetOpen}
        destructive
        title={t('methodology.confirm_remove_level')}
        body={t('methodology.confirm.remove_level_body')}
        confirmLabel={t('common.delete')}
        onCancel={onRemoveLevelCancel}
        onConfirm={onRemoveLevelConfirm}
      />

      <ReasonRequiredDialog
        open={archiveOpen}
        title={t('methodology.confirm.archive_title')}
        body={t('methodology.confirm.archive_body')}
        onCancel={onArchiveCancel}
        onConfirm={onArchiveConfirm}
      />

      {/* FE-1 — first-edit confirm gate for approved-version edits. Reuses the
          shared ConfirmDialog; once acknowledged, edits flow for the page
          session. */}
      <ConfirmDialog
        open={approvedEditConfirmOpen}
        title={t('methodology.approved_edit.confirm_title')}
        body={t('methodology.approved_edit.confirm_body')}
        confirmLabel={t('methodology.approved_edit.confirm_action')}
        onCancel={onApprovedEditCancel}
        onConfirm={onApprovedEditConfirm}
      />

      <ConfirmDialog
        open={newVersionOpen}
        title={t('methodology.confirm.new_version_title')}
        body={t('methodology.confirm.new_version_body')}
        confirmLabel={t('methodology.create_new_version')}
        onCancel={onNewVersionCancel}
        onConfirm={onNewVersionConfirm}
      />

      <SaveAsTemplateDrawer
        open={saveTemplateOpen}
        methodology={methodology}
        onClose={onSaveTemplateClose}
        onSubmit={onSaveTemplateSubmit}
      />

      {/* Edit methodology + DRAFT version metadata. `editable` is true only when
          the version is editable (DRAFT) so APPROVED/LOCKED never expose the
          type/scoring fields — the backend also enforces DRAFT-only. */}
      <MethodologyMetadataDrawer
        open={metadataOpen}
        methodology={methodology}
        version={version}
        editable={!readOnly}
        onClose={onMetadataClose}
        onSubmit={onMetadataSubmit}
      />
    </>
  );
}
