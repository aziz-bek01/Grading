import { z } from 'zod';
import type { QuestionType } from '../types';

/** Zod schema per question type — used by QuestionRenderer to validate answers. */
export const textAnswerSchema = z.string().trim().min(1, 'validation_required').max(500);
export const longTextAnswerSchema = z.string().trim().min(1, 'validation_required').max(4000);
export const singleChoiceSchema = z.string().min(1, 'validation_required');
export const multiChoiceSchema = z.array(z.string()).min(1, 'validation_required');
export const ratingSchema = z.number().int().min(1).max(5);
export const numberSchema = z.number().finite();

export function schemaFor(type: QuestionType) {
  switch (type) {
    case 'TEXT':
      return textAnswerSchema;
    case 'LONG_TEXT':
      return longTextAnswerSchema;
    case 'SINGLE_CHOICE':
      return singleChoiceSchema;
    case 'MULTI_CHOICE':
      return multiChoiceSchema;
    case 'RATING_SCALE':
      return ratingSchema;
    case 'NUMBER':
      return numberSchema;
    default:
      return z.unknown();
  }
}

export function isAnswerComplete(type: QuestionType, value: unknown): boolean {
  if (value === null || value === undefined) return false;
  const result = schemaFor(type).safeParse(value);
  return result.success;
}
