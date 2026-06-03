import { z } from 'zod';

export const COMMENT_MAX_LENGTH = 5000;

export const commentBodySchema = z.object({
  body: z
    .string({ error: 'body_required' })
    .trim()
    .min(1, { message: 'body_too_short' })
    .max(COMMENT_MAX_LENGTH, { message: 'body_too_long' }),
});

export type CommentBodyInput = z.infer<typeof commentBodySchema>;
