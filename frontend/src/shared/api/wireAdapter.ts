/**
 * Shared wire → domain adapter primitives.
 *
 * The real backend serialises EVERY response in SNAKE_CASE (global Jackson
 * `spring.jackson.property-naming-strategy: SNAKE_CASE`) with
 * `@JsonInclude(NON_NULL)` — null fields are OMITTED, never sent. Most feature
 * domain types and their consumers read camelCase, so every feature API client
 * needs the same snake-first / camelCase-fallback field reader and the same
 * paged-envelope mapper.
 *
 * This module is the single home for that boilerplate (previously copy-pasted
 * into exportApi / importApi / reportApi / approvalApi / workflowApi — jscpd
 * flagged 29–34 duplicated lines each). The helpers are intentionally tiny and
 * leak-free: each feature still declares its OWN field-by-field normaliser and
 * its OWN endpoints/payload shapes. Only the primitives are shared.
 *
 * Reading snake_case FIRST with a camelCase fallback keeps BOTH the in-process
 * MSW mock (camelCase fixtures) and the real backend (snake_case) deserialising
 * correctly — no behaviour change versus the per-feature copies.
 */

/** Opaque untyped wire object (a JSON record before normalisation). */
export type Raw = Record<string, unknown>;

/**
 * `(raw[snake] ?? raw[camel]) ?? null` — read snake_case first with a
 * camelCase fallback, defaulting to `null` when both are absent.
 */
export function pick<T = string>(raw: Raw, snake: string, camel: string): T | null {
  return ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
}

/**
 * Boolean variant of {@link pick}: `Boolean(raw[snake] ?? raw[camel] ?? false)`.
 * Used for the `contains_salary_data` / `contains_personal_data` flags.
 */
export function pickBool(raw: Raw, snake: string, camel: string): boolean {
  return Boolean(raw[snake] ?? raw[camel] ?? false);
}

/** Camel-cased page envelope every paged list fetcher returns. */
export interface PageEnvelope<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Maps a backend `PageResponse` envelope (`{ items, page, size,
 * total_elements, total_pages }`, snake_case on the wire with a camelCase
 * fallback for the MSW mock) into the camelCase {@link PageEnvelope} shape,
 * normalising each item through `mapItem`. Missing fields default exactly as
 * the previous per-feature copies did: `page → 0`, `size → items.length`,
 * `totalElements → items.length`, `totalPages → 1`.
 */
export function mapPageEnvelope<T>(
  data: unknown,
  mapItem: (raw: unknown) => T,
): PageEnvelope<T> {
  const env = (data ?? {}) as Raw;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => mapItem(r)),
    page: Number(env.page ?? 0),
    size: Number(env.size ?? rawItems.length),
    totalElements: Number(env.total_elements ?? env.totalElements ?? rawItems.length),
    totalPages: Number(env.total_pages ?? env.totalPages ?? 1),
  };
}
