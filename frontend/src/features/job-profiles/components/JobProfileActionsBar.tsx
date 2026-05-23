import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Save, Send, Check, RotateCw, Archive, FilePlus2, AlertOctagon } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { JobProfile } from '../types';

interface JobProfileActionsBarProps {
  profile: JobProfile;
  saving?: boolean;
  onSave?: () => void;
  onSubmit?: () => void;
  onApprove?: () => void;
  onRequestChanges?: (reason: string) => void;
  onArchive?: (reason: string) => void;
  onCreateRevision?: () => void;
}

/** Status-aware actions panel — sits in the right rail of the editor. */
export function JobProfileActionsBar({
  profile,
  saving,
  onSave,
  onSubmit,
  onApprove,
  onRequestChanges,
  onArchive,
  onCreateRevision,
}: JobProfileActionsBarProps) {
  const { t } = useTranslation();
  const [approveOpen, setApproveOpen] = useState(false);
  const [reqChangesOpen, setReqChangesOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [revisionOpen, setRevisionOpen] = useState(false);

  const status = profile.status;

  return (
    <Card compact title={t('jobProfile.actions_title')}>
      {status === 'ARCHIVED' ? (
        <div
          className="rounded-md bg-locked-bg border border-border p-3 text-sm text-text-secondary inline-flex items-center gap-2"
          role="status"
          data-testid="archived-banner"
        >
          <Archive size={16} aria-hidden />
          {t('jobProfile.archived_banner')}
        </div>
      ) : null}

      <div className="space-y-2">
        {status === 'DRAFT' ? (
          <>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <Button
                variant="secondary"
                fullWidth
                onClick={onSave}
                leadingIcon={<Save size={16} />}
                disabled={saving}
                data-testid="action-save"
              >
                {saving ? t('app.loading') : t('common.save_draft')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <Button
                variant="primary"
                fullWidth
                onClick={onSubmit}
                leadingIcon={<Send size={16} />}
                data-testid="action-submit"
              >
                {t('jobProfile.actions.submit_for_review')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <Button
                variant="ghost"
                fullWidth
                onClick={() => setArchiveOpen(true)}
                leadingIcon={<Archive size={16} />}
                data-testid="action-archive"
              >
                {t('common.archive')}
              </Button>
            </PermissionGate>
          </>
        ) : null}

        {status === 'UNDER_REVIEW' ? (
          <>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_APPROVE}>
              <Button
                variant="primary"
                fullWidth
                onClick={() => setApproveOpen(true)}
                leadingIcon={<Check size={16} />}
                data-testid="action-approve"
              >
                {t('common.approve')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_APPROVE}>
              <Button
                variant="secondary"
                fullWidth
                onClick={() => setReqChangesOpen(true)}
                leadingIcon={<RotateCw size={16} />}
                data-testid="action-request-changes"
              >
                {t('jobProfile.actions.request_changes')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <Button
                variant="ghost"
                fullWidth
                onClick={() => setArchiveOpen(true)}
                leadingIcon={<Archive size={16} />}
                data-testid="action-archive"
              >
                {t('common.archive')}
              </Button>
            </PermissionGate>
          </>
        ) : null}

        {status === 'APPROVED' ? (
          <>
            <div
              className="rounded-md bg-locked-bg border border-border p-3 text-xs text-text-secondary"
              data-testid="approved-locked-notice"
            >
              <span className="inline-flex items-center gap-2">
                <AlertOctagon size={14} aria-hidden /> {t('jobProfile.approved_locked_notice')}
              </span>
            </div>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <Button
                variant="primary"
                fullWidth
                onClick={() => setRevisionOpen(true)}
                leadingIcon={<FilePlus2 size={16} />}
                data-testid="action-create-revision"
              >
                {t('jobProfile.actions.create_revision')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <Button
                variant="ghost"
                fullWidth
                onClick={() => setArchiveOpen(true)}
                leadingIcon={<Archive size={16} />}
                data-testid="action-archive"
              >
                {t('common.archive')}
              </Button>
            </PermissionGate>
          </>
        ) : null}
      </div>

      <ConfirmDialog
        open={approveOpen}
        title={t('jobProfile.confirm.approve_title')}
        body={t('jobProfile.confirm.approve_body')}
        confirmLabel={t('common.approve')}
        onCancel={() => setApproveOpen(false)}
        onConfirm={() => {
          setApproveOpen(false);
          onApprove?.();
        }}
      />

      <ReasonRequiredDialog
        open={reqChangesOpen}
        title={t('jobProfile.confirm.request_changes_title')}
        body={t('jobProfile.confirm.request_changes_body')}
        onCancel={() => setReqChangesOpen(false)}
        onConfirm={(reason) => {
          setReqChangesOpen(false);
          onRequestChanges?.(reason);
        }}
      />

      <ReasonRequiredDialog
        open={archiveOpen}
        title={t('jobProfile.confirm.archive_title')}
        body={t('jobProfile.confirm.archive_body')}
        onCancel={() => setArchiveOpen(false)}
        onConfirm={(reason) => {
          setArchiveOpen(false);
          onArchive?.(reason);
        }}
      />

      <ConfirmDialog
        open={revisionOpen}
        title={t('jobProfile.confirm.revision_title')}
        body={t('jobProfile.confirm.revision_body')}
        confirmLabel={t('jobProfile.actions.create_revision')}
        onCancel={() => setRevisionOpen(false)}
        onConfirm={() => {
          setRevisionOpen(false);
          onCreateRevision?.();
        }}
      />
    </Card>
  );
}
