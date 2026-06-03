import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import {
  useComments,
  useCreateComment,
  useDeleteComment,
  useUpdateComment,
} from '../hooks/useComments';
import { CommentCard } from './CommentCard';
import { CommentInput } from './CommentInput';
import type { Comment, CommentEntityType } from '../types';

interface Props {
  entityType: CommentEntityType;
  entityId: string | undefined;
}

/**
 * Reusable comment thread (design-foundation §16.7).
 *  - Lists comments newest-first.
 *  - Supports a single level of replies (no deeper nesting in MVP 2 P1).
 *  - Edit / delete gated by COMMENT_EDIT / COMMENT_DELETE permission AND ownership.
 *  - Mentions rendered via `MentionText` chip.
 */
export function CommentThread({ entityType, entityId }: Props) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const list = useComments(entityType, entityId);
  const create = useCreateComment(entityType, entityId);
  const update = useUpdateComment(entityType, entityId);
  const del = useDeleteComment(entityType, entityId);

  const canPost = !!user?.permissions.includes(PERMISSIONS.COMMENT_CREATE);

  const topLevel = useMemo<Comment[]>(() => {
    const items = list.data?.items ?? [];
    // If backend already nests replies, just return root-level items.
    if (items.some((i) => Array.isArray(i.replies))) {
      return items.filter((i) => !i.parentCommentId);
    }
    // Otherwise build a 1-level map.
    const byParent = new Map<string, Comment[]>();
    const roots: Comment[] = [];
    for (const c of items) {
      if (c.parentCommentId) {
        const arr = byParent.get(c.parentCommentId) ?? [];
        arr.push(c);
        byParent.set(c.parentCommentId, arr);
      } else {
        roots.push(c);
      }
    }
    return roots.map((r) => ({ ...r, replies: byParent.get(r.id) ?? [] }));
  }, [list.data]);

  if (list.isLoading) return <LoadingState />;
  if (list.error) return <ErrorState onRetry={() => list.refetch()} />;

  return (
    <div className="space-y-4" data-testid="comment-thread">
      {canPost && entityId ? (
        <CommentInput
          placeholder={t('comment.input_placeholder')}
          onSubmit={(body) =>
            create.mutateAsync({ entityType, entityId, body }).then(() => undefined)
          }
        />
      ) : null}

      {topLevel.length === 0 ? (
        <p className="text-sm text-text-secondary">{t('comment.empty')}</p>
      ) : (
        <ul className="space-y-4">
          {topLevel.map((c) => (
            <li key={c.id} className="border border-border rounded-lg p-3 bg-surface">
              <CommentCard
                comment={c}
                onEdit={async (id, body) =>
                  update.mutateAsync({ id, payload: { body } }).then(() => undefined)
                }
                onDelete={async (id) => del.mutateAsync(id).then(() => undefined)}
                onReply={async (parentId, body) =>
                  create
                    .mutateAsync({ entityType, entityId: entityId!, body, parentCommentId: parentId })
                    .then(() => undefined)
                }
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
