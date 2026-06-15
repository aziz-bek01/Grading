/**
 * Presence guard for the T3 (panel management) + T4 (subtree counts) i18n keys.
 *
 * Complements the broad i18nParity guard with an EXPLICIT list of the new keys,
 * so a regression that drops one of them fails with a precise name (not just a
 * count mismatch). Every key MUST resolve in all four shipped locales.
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

// The exact keys T3 + T4 add (dot notation).
const NEW_KEYS = [
  // T3 — panel list surface
  'panel.list.title',
  'panel.list.col_evaluators',
  'panel.list.evaluators_value',
  'panel.list.col_created',
  'panel.list.open',
  'panel.list.empty_title',
  'panel.list.empty_body',
  // T3 — panel detail + management actions
  'panel.detail.subtitle',
  'panel.detail.reopen',
  'panel.detail.archive',
  'panel.detail.no_actions',
  'panel.detail.delete_title',
  'panel.detail.delete_body',
  'panel.detail.delete_reason_placeholder',
  'panel.detail.archive_title',
  'panel.detail.archive_body',
  'panel.detail.archive_reason_placeholder',
  'panel.detail.not_found_title',
  'panel.detail.not_found_body',
  'panel.detail.back_to_list',
  // T3 — wizard un-dead-end CTA
  'panel.wizard.positions.open_existing',
  // T4 — wizard subtree toggle + badges
  'panel.wizard.positions.include_subtree',
  'panel.wizard.dept.subtree_count',
  'panel.wizard.dept.subtree_tooltip',
  // T4 — progress strip subtree figure
  'panel.dept_progress.subtree',
  'panel.dept_progress.subtree_tooltip',
  // Feature 1 — evaluator self-inbox
  'nav.my_evaluations',
  'my_evaluations.title',
  'my_evaluations.subtitle',
  'my_evaluations.empty_title',
  'my_evaluations.empty_body',
  'my_evaluations.select_project_hint',
  // Feature 2 — reopen for additional expert
  'panel.reopen_expert.action',
  'panel.reopen_expert.title',
  'panel.reopen_expert.consequence',
  'panel.reopen_expert.evaluator_label',
  'panel.reopen_expert.evaluator_required',
  'panel.reopen_expert.reason_placeholder',
  'panel.reopen_expert.reason_invalid',
  'panel.reopen_expert.confirm',
  'panel.reopen_expert.error_not_approved',
  'panel.reopen_expert.error_forbidden',
  'panel.reopen_expert.error_not_found',
  'panel.reopen_expert.error_validation',
  'panel.reopen_expert.error_generic',
] as const;

function resolve(bundle: unknown, dotKey: string): unknown {
  return dotKey.split('.').reduce<unknown>((acc, part) => {
    if (acc && typeof acc === 'object' && part in (acc as Record<string, unknown>)) {
      return (acc as Record<string, unknown>)[part];
    }
    return undefined;
  }, bundle);
}

describe('T3 + T4 i18n key presence', () => {
  for (const [name, bundle] of Object.entries(LOCALES)) {
    it(`${name} carries every new T3/T4 key as a non-empty string`, () => {
      const missing = NEW_KEYS.filter((k) => {
        const v = resolve(bundle, k);
        return typeof v !== 'string' || v.length === 0;
      });
      expect(missing, `Missing/empty in ${name}: ${missing.join(', ')}`).toEqual([]);
    });
  }
});
