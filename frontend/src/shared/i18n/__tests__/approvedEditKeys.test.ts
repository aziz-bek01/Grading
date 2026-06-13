/**
 * Presence guard for the FE-1 (approved-version edit) + FE-2 (delete → deprecate)
 * i18n keys. Complements the broad i18nParity guard with an EXPLICIT list, so a
 * regression that drops one fails with a precise name. Every key MUST resolve in
 * all four shipped locales (no raw keys).
 */
import { describe, expect, it } from 'vitest';
import ruRU from '../locales/ru-RU.json';
import uzCyrl from '../locales/uz-Cyrl-UZ.json';
import uzLatn from '../locales/uz-Latn-UZ.json';
import enUS from '../locales/en-US.json';

const LOCALES: Record<string, unknown> = {
  'ru-RU': ruRU,
  'uz-Cyrl-UZ': uzCyrl,
  'uz-Latn-UZ': uzLatn,
  'en-US': enUS,
};

const NEW_KEYS = [
  'common.dismiss',
  // FE-2 — deprecated badge
  'methodology.deprecated_badge',
  // FE-1 — approved-edit banner + first-edit confirm
  'methodology.approved_edit.banner_title',
  'methodology.approved_edit.banner_body',
  'methodology.approved_edit.confirm_title',
  'methodology.approved_edit.confirm_body',
  'methodology.approved_edit.confirm_action',
  // FE-2 — deprecate outcome explanations
  'methodology.deprecate.factor_deprecated',
  'methodology.deprecate.factor_referenced',
  'methodology.deprecate.level_deprecated',
  'methodology.deprecate.level_referenced',
] as const;

function resolve(bundle: unknown, dotKey: string): unknown {
  return dotKey.split('.').reduce<unknown>((acc, part) => {
    if (acc && typeof acc === 'object' && part in (acc as Record<string, unknown>)) {
      return (acc as Record<string, unknown>)[part];
    }
    return undefined;
  }, bundle);
}

describe('FE-1 + FE-2 i18n key presence', () => {
  for (const [name, bundle] of Object.entries(LOCALES)) {
    it(`${name} carries every new approved-edit/deprecate key as a non-empty string`, () => {
      const missing = NEW_KEYS.filter((k) => {
        const v = resolve(bundle, k);
        return typeof v !== 'string' || v.length === 0;
      });
      expect(missing, `Missing/empty in ${name}: ${missing.join(', ')}`).toEqual([]);
    });
  }
});
