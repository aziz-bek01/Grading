import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
import type { Methodology, SaveAsTemplatePayload } from '../types';

interface SaveAsTemplateDrawerProps {
  open: boolean;
  /** The methodology being snapshotted (seeds the suggested name). */
  methodology: Methodology | null;
  onClose: () => void;
  onSubmit: (payload: SaveAsTemplatePayload) => Promise<void>;
}

/**
 * "Save as template" drawer (Epic E).
 *
 * Snapshots a methodology's latest version into a reusable tenant CUSTOM
 * template (POST /methodologies/{id}/save-as-template). The consultant picks a
 * unique template `code` plus a localized name (ru-RU required) and optional
 * description. The backend duplicate-code conflict
 * (409 METHODOLOGY_TEMPLATE_CODE_EXISTS) is surfaced inline on the code field.
 *
 * Reuses DrawerForm + LocalizedNameTabs — no new primitives.
 */
export function SaveAsTemplateDrawer({ open, methodology, ...rest }: SaveAsTemplateDrawerProps) {
  // Keyed remount per methodology so the body re-seeds from props each open.
  if (!open || !methodology) return null;
  return <SaveAsTemplateDrawerBody key={methodology.id} methodology={methodology} {...rest} />;
}

function SaveAsTemplateDrawerBody({
  methodology,
  onClose,
  onSubmit,
}: Omit<SaveAsTemplateDrawerProps, 'open'> & { methodology: Methodology }) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [name, setName] = useState<LocalizedString>(methodology.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(
    methodology.description_i18n ?? {},
  );
  const [error, setError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    setCodeError(null);
    if (!code.trim()) {
      setCodeError(t('methodology.save_as_template.code_required'));
      return;
    }
    const ru = (name['ru-RU'] ?? '').trim();
    if (!ru) {
      setError(t('methodology.metadata.name_primary_required'));
      return;
    }
    const payload: SaveAsTemplatePayload = {
      code: code.trim(),
      name_i18n: name,
      description_i18n: description,
    };
    setSubmitting(true);
    try {
      await onSubmit(payload);
    } catch (e) {
      // Surface the backend duplicate-code conflict inline on the code field.
      if (
        e instanceof ApiError &&
        (e.code === 'METHODOLOGY_TEMPLATE_CODE_EXISTS' || e.status === 409)
      ) {
        setCodeError(t('methodology.save_as_template.duplicate_code'));
      } else if (e instanceof ApiError && e.fieldErrors?.code) {
        setCodeError(e.fieldErrors.code);
      } else {
        setError(t('methodology.save_as_template.failed'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('methodology.save_as_template.title')}
      subtitle={t('methodology.save_as_template.subtitle')}
      onClose={onClose}
      submitLabel={t('methodology.save_as_template.submit')}
      onSubmit={() => {
        if (!submitting) void handleSubmit();
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>
            {t('methodology.save_as_template.code_label')}{' '}
            <span className="text-danger-700">*</span>
          </span>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="TPL-CFO-2026"
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
            data-testid="save-template-code"
          />
          {codeError ? (
            <span
              className="mt-1 block text-xs text-danger-700"
              role="alert"
              data-testid="save-template-code-error"
            >
              {codeError}
            </span>
          ) : null}
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

        <p className="text-xs text-text-muted">{t('methodology.save_as_template.hint')}</p>

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid="save-template-error">
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
