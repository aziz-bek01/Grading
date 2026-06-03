/**
 * Comment domain types — mirror the backend Comment + CommentMention contracts.
 *
 * The body field supports inline mentions encoded as `@[uuid|Display Name]`
 * (renderer parses and styles them; backend audits them via CommentMention rows).
 */
import type { ApprovalEntityType } from '@/features/approval/types';

/** Comment can be attached to any of these entity types (mirrors backend enum). */
export type CommentEntityType =
  | ApprovalEntityType
  | 'POSITION'
  | 'DEPARTMENT';

export interface CommentMention {
  userId: string;
  userName?: string | null;
}

export interface Comment {
  id: string;
  entityType: CommentEntityType;
  entityId: string;
  parentCommentId?: string | null;
  authorUserId: string;
  authorName?: string | null;
  body: string;
  createdAt: string;
  updatedAt?: string | null;
  deletedAt?: string | null;
  mentions: CommentMention[];
  replies?: Comment[];
}

export interface CommentCreatePayload {
  entityType: CommentEntityType;
  entityId: string;
  body: string;
  parentCommentId?: string | null;
}

export interface CommentUpdatePayload {
  body: string;
}

export interface CommentListResponse {
  items: Comment[];
}
