import { z } from 'zod';
import { localizedStringAnyRequired } from '@/shared/lib/localizedSchema';

const localizedStringSchema = localizedStringAnyRequired({
  maxLength: 180,
  message: 'validation_primary_name_required',
});

export const DepartmentCreateSchema = z.object({
  project_id: z.string().uuid().or(z.string().min(1)),
  parent_id: z.string().uuid().or(z.string().min(1)).nullable(),
  code: z
    .string()
    .trim()
    .min(1)
    .max(32)
    .regex(/^[A-Z0-9][A-Z0-9-]*$/u, 'code'),
  /** Backend snake_case contract — POST body field. */
  name_i18n: localizedStringSchema,
  type: z.enum(['BRANCH', 'DEPARTMENT', 'DIVISION', 'UNIT']),
});

export type DepartmentCreateInput = z.infer<typeof DepartmentCreateSchema>;
