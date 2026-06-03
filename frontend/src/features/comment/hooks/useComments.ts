import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  commentKeys,
  createComment,
  deleteComment,
  fetchComments,
  fetchMyMentions,
  updateComment,
} from '../api/commentApi';
import type {
  CommentCreatePayload,
  CommentEntityType,
  CommentUpdatePayload,
} from '../types';

export function useComments(entityType: CommentEntityType, entityId: string | undefined) {
  return useQuery({
    queryKey: entityId ? commentKeys.list(entityType, entityId) : ['comments', 'list', null],
    queryFn: () => fetchComments(entityType, entityId!),
    enabled: !!entityId,
  });
}

export function useCreateComment(entityType: CommentEntityType, entityId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CommentCreatePayload) => createComment(payload),
    onSuccess: () => {
      if (entityId) qc.invalidateQueries({ queryKey: commentKeys.list(entityType, entityId) });
    },
  });
}

export function useUpdateComment(entityType: CommentEntityType, entityId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: string; payload: CommentUpdatePayload }) =>
      updateComment(vars.id, vars.payload),
    onSuccess: () => {
      if (entityId) qc.invalidateQueries({ queryKey: commentKeys.list(entityType, entityId) });
    },
  });
}

export function useDeleteComment(entityType: CommentEntityType, entityId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteComment(id),
    onSuccess: () => {
      if (entityId) qc.invalidateQueries({ queryKey: commentKeys.list(entityType, entityId) });
    },
  });
}

export function useMyMentions() {
  return useQuery({
    queryKey: commentKeys.myMentions,
    queryFn: fetchMyMentions,
  });
}
