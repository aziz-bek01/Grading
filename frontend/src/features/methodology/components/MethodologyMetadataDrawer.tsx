import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import type { LocalizedString } from '@/shared/types/common';
import type { Methodology, MethodologyUpdatePayload } from '../types';

interface MethodologyMetadataDrawerProps {
  open: boolean;
  methodology: Methodology | null;
  onClose: () => void;
  onSubmit: (patch: MethodologyUpdatePayload) => void | Promise<void>;
}

/**
 * Drawer for editing a methodology's container METADATA (rename / description).
 *
 * The container `code` is immutable post-create (set by
 * CreateMethodologyFromScratch/FromTemplate) — shown read-only here, never sent
 * in the PATCH body (honest contract; see MethodologyUpdatePayload).
 *
 * Reuses DrawerForm + LocalizedNameTabs (no new primitives).
 */
export function MethodologyMetadataDrawer({ open, methodology, ...rest }: MethodologyMetadataDrawerProps) {
  // Keyed remount per methodology so the body seeds straight from props.
  if (!open || !methodology) return null;
  return <MethodologyMetadataDrawerBody key={methodology.id} methodology={methodology} {...rest} />;
}

function MethodologyMetadataDrawerBody({
  methodology,
  onClose,
  onSubmit,
}: Omit<MethodologyMetadataDrawerProps, 'open'> & { methodology: Methodology }) {
  const { t } = useTranslation();
  const [name, setName] = useState<LocalizedString>(methodology.name_i18n ?? {});
  const [description, setDescription] = useState<LocalizedString>(methodology.description_i18n ?? {});
  const [error, setError] = useState<string | null>(null);

  return (
    <DrawerForm
      open
      title={t('methodology.metadata.edit_title')}
      subtitle={t('methodology.metadata.edit_subtitle')}
      onClose={onClose}
      onSubmit={() => {
        const ru = (name['ru-RU'] ?? '').trim();
        if (!ru) {
          setError(t('methodology.metadata.name_primary_required'));
          return;
        }
        setError(null);
        // `code` is intentionally NOT part of the payload (immutable container).
        void onSubmit({ name_i18n: name, description_i18n: description });
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>{t('common.code')}</span>
          <input
            type="text"
            value={methodology.code}
            readOnly
            disabled
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-divider text-text-secondary font-mono cursor-not-allowed"
            data-testid="metadata-code"
          />
          <span className="mt-1 block text-xs text-text-muted">
            {t('methodology.metadata.code_immutable')}
          </span>
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid="metadata-error">
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
