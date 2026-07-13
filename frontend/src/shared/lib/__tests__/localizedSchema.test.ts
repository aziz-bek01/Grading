import { describe, expect, it } from 'vitest';
import {
  localeEnum,
  localizedStringAnyRequired,
  localizedStringOptional,
  localizedStringRequiredPrimary,
} from '../localizedSchema';

describe('localizedStringRequiredPrimary', () => {
  const schema = localizedStringRequiredPrimary({ maxLength: 8 });

  it('accepts a value with only ru-RU filled', () => {
    const result = schema.safeParse({ 'ru-RU': 'Название' });
    expect(result.success).toBe(true);
  });

  it('rejects a blank ru-RU with the default message key', () => {
    const result = schema.safeParse({ 'ru-RU': '' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('validation_primary_required');
    }
  });

  it('rejects a missing ru-RU key entirely', () => {
    const result = schema.safeParse({});
    expect(result.success).toBe(false);
  });

  it('enforces maxLength per locale', () => {
    const result = schema.safeParse({ 'ru-RU': 'toolongvalue' });
    expect(result.success).toBe(false);
  });

  it('honours a custom requiredMessage', () => {
    const custom = localizedStringRequiredPrimary({ maxLength: 8, requiredMessage: 'custom_key' });
    const result = custom.safeParse({ 'ru-RU': '' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('custom_key');
    }
  });

  it('strips unknown keys by default (passthrough not set)', () => {
    const result = schema.safeParse({ 'ru-RU': 'ok', extra: 'x' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect('extra' in result.data).toBe(false);
    }
  });

  it('keeps unknown keys when passthrough is true', () => {
    const withPassthrough = localizedStringRequiredPrimary({ maxLength: 8, passthrough: true });
    const result = withPassthrough.safeParse({ 'ru-RU': 'ok', extra: 'x' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect((result.data as Record<string, unknown>).extra).toBe('x');
    }
  });
});

describe('localizedStringOptional', () => {
  const schema = localizedStringOptional({ maxLength: 5 });

  it('accepts an empty object (every locale optional)', () => {
    expect(schema.safeParse({}).success).toBe(true);
  });

  it('enforces maxLength when a locale is provided', () => {
    expect(schema.safeParse({ 'ru-RU': 'toolong' }).success).toBe(false);
    expect(schema.safeParse({ 'ru-RU': 'ok' }).success).toBe(true);
  });
});

describe('localizedStringAnyRequired', () => {
  const schema = localizedStringAnyRequired({ maxLength: 10, message: 'validation_title_required' });

  it('accepts when only a non-primary locale is filled', () => {
    const result = schema.safeParse({ 'en-US': 'Title' });
    expect(result.success).toBe(true);
  });

  it('rejects when every locale is blank/absent', () => {
    const result = schema.safeParse({ 'ru-RU': '', 'en-US': undefined });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('validation_title_required');
    }
  });

  it('uses the default message when none is supplied', () => {
    const defaultMsg = localizedStringAnyRequired({ maxLength: 10 });
    const result = defaultMsg.safeParse({});
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('validation_primary_name_required');
    }
  });
});

describe('localeEnum', () => {
  it('accepts all four supported locales', () => {
    for (const l of ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US']) {
      expect(localeEnum.safeParse(l).success).toBe(true);
    }
  });

  it('rejects an unsupported locale', () => {
    expect(localeEnum.safeParse('fr-FR').success).toBe(false);
  });
});
