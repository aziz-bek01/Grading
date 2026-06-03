import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  Comment,
  CommentCreatePayload,
  CommentEntityType,
  CommentListResponse,
  CommentUpdatePayload,
} from '../types';

export const commentKeys = {
  all: ['comments'] as const,
  list: (entityType: CommentEntityType, entityId: string) =>
    ['comments', 'list', entityType, entityId] as const,
  myMentions: ['comments', 'my-mentions'] as const,
};

export async function createComment(payload: CommentCreatePayload): Promise<Comment> {
  const res = await httpClient.post<Comment>(endpoints.comments.list, payload);
  return res.data;
}

export async function fetchComments(
  entityType: CommentEntityType,
  entityId: string,
): Promise<CommentListResponse> {
  const res = await httpClient.get<CommentListResponse>(endpoints.comments.list, {
    params: { entityType, entityId },
  });
  return res.data;
}

export async function updateComment(id: string, payload: CommentUpdatePayload): Promise<Comment> {
  const res = await httpClient.patch<Comment>(endpoints.comments.detail(id), payload);
  return res.data;
}

export async function deleteComment(id: string): Promise<void> {
  await httpClient.delete<void>(endpoints.comments.detail(id));
}

export async function fetchMyMentions(): Promise<CommentListResponse> {
  const res = await httpClient.get<CommentListResponse>(endpoints.comments.myMentions);
  return res.data;
}
