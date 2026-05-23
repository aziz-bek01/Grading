/**
 * Locale key parity guard (D-007 / C-5 / D-205).
 *
 * Loads all 4 supported locale JSONs, flattens their key sets into dot
 * notation, and asserts that every locale carries the SAME set of keys.
 *
 * Why this matters:
 *   - We ship four UIs (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US).
 *   - If one locale drifts (e.g. a developer adds a key to en-US.json only),
 *     users on the other locales see the raw key as a string. That is the
 *     class of bug behind C-5 / D-205.
 *
 * Failure output:
 *   The test names the offending locale(s) and lists exactly which keys
 *   are missing — so the fix is to add those keys to the listed file.
 *
 * Adding a new key:
 *   1. Add it to ALL four files (or this test fails).
 *   2. Translate. For uz-Cyrl-UZ and uz-Latn-UZ use the same translation
 *      in different scripts.
 *   3. Run `npm test -- i18nParity` to confirm.
 */
import { describe, expect, it } from 'vitest';
import ruRU from '../locales/ru-RU.json';
import uzCyrl from '../locales/uz-Cyrl-UZ.json';
import uzLatn from '../locales/uz-Latn-UZ.json';
import enUS from '../locales/en-US.json';

type Bundle = Record<string, unknown>;

const LOCALES: Record<string, Bundle> = {
  'ru-RU': ruRU as Bundle,
  'uz-Cyrl-UZ': uzCyrl as Bundle,
  'uz-Latn-UZ': uzLatn as Bundle,
  'en-US': enUS as Bundle,
};

function flattenKeys(obj: unknown, prefix = '', out: Set<string> = new Set()): Set<string> {
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) return out;
  for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
    const composed = prefix ? `${prefix}.${k}` : k;
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      flattenKeys(v, composed, out);
    } else {
      out.add(composed);
    }
  }
  return out;
}

describe('i18n locale parity', () => {
  const keySets: Record<string, Set<string>> = {};
  for (const [name, bundle] of Object.entries(LOCALES)) {
    keySets[name] = flattenKeys(bundle);
  }
  const union = new Set<string>();
  for (const set of Object.values(keySets)) set.forEach((k) => union.add(k));

  it('each locale carries the full union of keys', () => {
    const report: Record<string, string[]> = {};
    for (const [name, set] of Object.entries(keySets)) {
      const missing = [...union].filter((k) => !set.has(k));
      if (missing.length > 0) report[name] = missing;
    }
    expect(
      report,
      `Locale parity broken. Missing keys per locale:\n${JSON.stringify(report, null, 2)}\n` +
        `Fix: add the listed keys to the named locale file(s) in src/shared/i18n/locales/.`,
    ).toEqual({});
  });

  it('no orphan keys (every key is in every locale)', () => {
    // Cross-check: for every locale a key appears in, it must appear in all locales.
    const orphans: Record<string, string[]> = {};
    for (const key of union) {
      const present = Object.entries(keySets)
        .filter(([, s]) => s.has(key))
        .map(([n]) => n);
      if (present.length !== Object.keys(LOCALES).length) {
        const absent = Object.keys(LOCALES).filter((n) => !present.includes(n));
        for (const a of absent) {
          (orphans[a] ??= []).push(key);
        }
      }
    }
    expect(
      orphans,
      `Orphan keys detected (defined in some locales but not all):\n${JSON.stringify(orphans, null, 2)}`,
    ).toEqual({});
  });

  it('all four locales agree on key count', () => {
    const counts = Object.fromEntries(
      Object.entries(keySets).map(([n, s]) => [n, s.size]),
    );
    const uniq = new Set(Object.values(counts));
    expect(uniq.size, `Locale key counts differ: ${JSON.stringify(counts, null, 2)}`).toBe(1);
  });
});
