import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Trash2, CornerDownRight } from 'lucide-react';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { formatDateSafe } from '@/shared/lib/dates';
import { MentionText } from './MentionText';
import { CommentInput } from './CommentInput';
import type { Comment } from '../types';

interface Props {
  comment: Comment;
  onEdit?: (id: string, body: string) => void | Promise<void>;
  onDelete?: (id: string) => void | Promise<void>;
  onReply?: (parentId: string, body: string) => void | Promise<void>;
  /** When true, no reply button is rendered (avoid nesting deeper than 1 level). */
  isReply?: boolean;
}

export function CommentCard({ comment, onEdit, onDelete, onReply, isReply }: Props) {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const [editing, setEditing] = useState(false);
  const [replying, setReplying] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const isOwn = !!user && user.id === comment.authorUserId;
  const canEdit = isOwn && !!onEdit && user?.permissions.includes(PERMISSIONS.COMMENT_EDIT);
  const canDelete = isOwn && !!onDelete && user?.permissions.includes(PERMISSIONS.COMMENT_DELETE);
  const canReply = !isReply && !!onReply && user?.permissions.includes(PERMISSIONS.COMMENT_CREATE);

  const created = formatDateSafe(comment.createdAt, i18n.language);
  const edited = comment.updatedAt && comment.updatedAt !== comment.createdAt;

  if (comment.deletedAt) {
    return (
      <div className="text-sm text-text-muted italic py-2" data-testid="comment-card-deleted">
        {t('comment.deleted_placeholder')}
      </div>
    );
  }

  return (
    <div className="space-y-2" data-testid="comment-card">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-sm font-medium text-text-primary">
            {comment.authorName ?? comment.authorUserId}
          </div>
          <div className="text-xs text-text-muted">
            {created} {edited ? `· ${t('comment.edited')}` : ''}
          </div>
        </div>
        <div className="flex gap-1">
          {canEdit ? (
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="text-text-muted hover:text-text-primary p-1"
              title={t('common.edit')}
              data-testid="comment-edit"
            >
              <Pencil size={14} />
            </button>
          ) : null}
          {canDelete ? (
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              className="text-text-muted hover:text-danger-700 p-1"
              title={t('common.delete')}
              data-testid="comment-delete"
            >
              <Trash2 size={14} />
            </button>
          ) : null}
        </div>
      </div>
      {editing ? (
        <CommentInput
          initialValue={comment.body}
          onCancel={() => setEditing(false)}
          submitLabel={t('common.save')}
          onSubmit={async (body) => {
            await onEdit?.(comment.id, body);
            setEditing(false);
          }}
        />
      ) : (
        <MentionText body={comment.body} />
      )}
      {canReply && !replying ? (
        <button
          type="button"
          onClick={() => setReplying(true)}
          className="inline-flex items-center gap-1 text-xs text-primary-700 hover:underline"
          data-testid="comment-reply-trigger"
        >
          <CornerDownRight size={12} /> {t('comment.reply')}
        </button>
      ) : null}
      {replying ? (
        <div className="ml-4 border-l-2 border-divider pl-3">
          <CommentInput
            compact
            placeholder={t('comment.reply_placeholder')}
            submitLabel={t('comment.submit_reply')}
            onCancel={() => setReplying(false)}
            onSubmit={async (body) => {
              await onReply?.(comment.id, body);
              setReplying(false);
            }}
          />
        </div>
      ) : null}
      {comment.replies && comment.replies.length > 0 ? (
        <ul className="ml-4 border-l-2 border-divider pl-3 mt-2 space-y-3">
          {comment.replies.map((r) => (
            <li key={r.id}>
              <CommentCard
                comment={r}
                onEdit={onEdit}
                onDelete={onDelete}
                isReply
              />
            </li>
          ))}
        </ul>
      ) : null}
      <ConfirmDialog
        open={confirmDelete}
        title={t('comment.delete_confirm_title')}
        body={t('comment.delete_confirm_body')}
        confirmLabel={t('common.delete')}
        destructive
        onCancel={() => setConfirmDelete(false)}
        onConfirm={() => {
          void onDelete?.(comment.id);
          setConfirmDelete(false);
        }}
      />
    </div>
  );
}
