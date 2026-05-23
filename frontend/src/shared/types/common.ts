export type ID = string;

export type EntityStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'LOCKED'
  | 'ARCHIVED'
  | 'INCOMPLETE';

export interface PageEnvelope<T> {
  items: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface ErrorEnvelope {
  code: string;
  message: string;
  correlation_id?: string;
  timestamp?: string;
  traceId?: string;
  fieldErrors?: Record<string, string>;
}

export type Locale = 'ru-RU' | 'uz-Cyrl-UZ' | 'uz-Latn-UZ' | 'en-US';

export type LocalizedString = Partial<Record<Locale, string>>;
