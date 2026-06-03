import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { COMMENT_MAX_LENGTH, commentBodySchema } from '../schemas/commentSchemas';

interface Props {
  onSubmit: (body: string) => void | Promise<void>;
  onCancel?: () => void;
  initialValue?: string;
  submitLabel?: string;
  placeholder?: string;
  /** When true, only render submit when there's a value (used for replies). */
  compact?: boolean;
  disabled?: boolean;
}

/**
 * CommentInput — textarea with character counter + @ trigger stub.
 * Mention autocomplete is stubbed: typing `@` shows a small "users come from
 * backend" hint, and the user can manually type `@[uuid|Display Name]` syntax.
 * Full mention autocomplete will land with the user-directory endpoint (MVP 2 P2).
 */
export function CommentInput({
  onSubmit,
  onCancel,
  initialValue = '',
  submitLabel,
  placeholder,
  compact,
  disabled,
}: Props) {
  const { t } = useTranslation();
  const [value, setValue] = useState(initialValue);
  const [error, setError] = useState<string | null>(null);
  const [showMentionHint, setShowMentionHint] = useState(false);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const parsed = commentBodySchema.safeParse({ body: value });
    if (!parsed.success) {
      const first = parsed.error.issues[0];
      setError(t(`comment.errors.${first.message}`));
      return;
    }
    setError(null);
    void onSubmit(parsed.data.body);
    setValue('');
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2" data-testid="comment-input">
      <textarea
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          setShowMentionHint(e.target.value.includes('@'));
        }}
        placeholder={placeholder ?? t('comment.input_placeholder')}
        rows={compact ? 2 : 3}
        maxLength={COMMENT_MAX_LENGTH}
        disabled={disabled}
        className="w-full rounded-md border border-border-strong p-3 text-sm focus:border-primary-500 focus:outline-none disabled:bg-divider"
      />
      {showMentionHint ? (
        <p className="text-xs text-text-muted">{t('comment.mention_hint')}</p>
      ) : null}
      <div className="flex items-center justify-between text-xs text-text-muted">
        <span data-testid="comment-counter">
          {value.length} / {COMMENT_MAX_LENGTH}
        </span>
        {error ? <span className="text-danger-700">{error}</span> : null}
      </div>
      <div className="flex justify-end gap-2">
        {onCancel ? (
          <Button variant="ghost" type="button" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
        ) : null}
        <Button type="submit" disabled={disabled || value.trim().length === 0}>
          {submitLabel ?? t('comment.submit')}
        </Button>
      </div>
    </form>
  );
}
