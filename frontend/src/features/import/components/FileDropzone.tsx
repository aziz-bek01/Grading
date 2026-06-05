/**
 * Drag-and-drop / click-to-pick uploader for .xlsx files only.
 *
 * Performs the same client-side gate-keeping as the Zod schema:
 * - extension .xlsx
 * - MIME application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
 * - size <= 25 MB
 *
 * Backend STILL re-checks every rule (integration-blueprint §10.1).
 */
import { useCallback, useId, useRef, useState, type DragEvent, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Upload, File as FileIcon, X } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { MAX_UPLOAD_BYTES, XLSX_MIME } from '../schemas/importSchemas';
import { formatBytes } from './formatBytes';

interface Props {
  file: File | null;
  onFileChange: (file: File | null) => void;
  /** Localised error string (already translated) or null. */
  errorMessage?: string | null;
  disabled?: boolean;
}

function validate(file: File): string | null {
  if (file.size === 0) return 'import.file.error.empty';
  if (file.size > MAX_UPLOAD_BYTES) return 'import.file.error.too_large';
  if (!file.name.toLowerCase().endsWith('.xlsx')) return 'import.file.error.extension';
  // Some browsers report no MIME for drag-drop; allow empty + xlsx ext.
  if (file.type && file.type !== XLSX_MIME) return 'import.file.error.mime';
  return null;
}

export function FileDropzone({ file, onFileChange, errorMessage, disabled }: Props) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const id = useId();

  const handleFiles = useCallback(
    (files: FileList | null) => {
      if (!files || files.length === 0) return;
      const f = files[0];
      const err = validate(f);
      if (err) {
        setLocalError(err);
        onFileChange(null);
        return;
      }
      setLocalError(null);
      onFileChange(f);
    },
    [onFileChange],
  );

  const onDrop = (ev: DragEvent<HTMLLabelElement>) => {
    ev.preventDefault();
    setDragOver(false);
    if (disabled) return;
    handleFiles(ev.dataTransfer.files);
  };

  const onDragOver = (ev: DragEvent<HTMLLabelElement>) => {
    ev.preventDefault();
    if (!disabled) setDragOver(true);
  };

  const onInputChange = (ev: ChangeEvent<HTMLInputElement>) => {
    handleFiles(ev.target.files);
    // Reset so the same file can be re-picked after removal.
    ev.target.value = '';
  };

  const remove = () => {
    setLocalError(null);
    onFileChange(null);
  };

  const errToShow = errorMessage ?? (localError ? t(localError) : null);

  return (
    <div className="space-y-2" data-testid="file-dropzone">
      {!file ? (
        <label
          htmlFor={id}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onDragLeave={() => setDragOver(false)}
          className={cn(
            'flex flex-col items-center justify-center gap-2 px-6 py-10 rounded-lg border-2 border-dashed cursor-pointer transition-colors',
            disabled
              ? 'opacity-50 cursor-not-allowed border-border'
              : dragOver
                ? 'border-primary-500 bg-primary-50'
                : 'border-border hover:border-primary-500 hover:bg-divider',
          )}
        >
          <Upload className="text-text-secondary" size={28} aria-hidden />
          <div className="text-sm text-text-primary font-medium">{t('import.file.cta')}</div>
          <div className="text-xs text-text-muted">{t('import.file.hint')}</div>
          <input
            id={id}
            ref={inputRef}
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            className="sr-only"
            onChange={onInputChange}
            disabled={disabled}
            data-testid="file-dropzone-input"
          />
        </label>
      ) : (
        <div
          className="flex items-center gap-3 p-3 rounded-md border border-border bg-surface"
          data-testid="file-dropzone-selected"
        >
          <FileIcon className="text-primary-500 shrink-0" size={24} aria-hidden />
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium truncate text-text-primary">{file.name}</div>
            <div className="text-xs text-text-muted">{formatBytes(file.size)}</div>
          </div>
          <button
            type="button"
            onClick={remove}
            className="p-1.5 rounded hover:bg-divider"
            aria-label={t('common.delete')}
            disabled={disabled}
            data-testid="file-dropzone-remove"
          >
            <X size={16} />
          </button>
        </div>
      )}
      {errToShow ? (
        <div
          role="alert"
          className="text-xs text-danger-700 bg-danger-50 border border-danger-500/30 rounded px-2 py-1"
          data-testid="file-dropzone-error"
        >
          {errToShow}
        </div>
      ) : null}
    </div>
  );
}
