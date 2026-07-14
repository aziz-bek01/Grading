import { describe, expect, it } from 'vitest';
import { fieldA11y, fieldErrorId } from '../fieldA11y';

describe('fieldErrorId', () => {
  it('appends the `-error` suffix to the given id', () => {
    expect(fieldErrorId('create-tenant-slug')).toBe('create-tenant-slug-error');
  });
});

describe('fieldA11y', () => {
  it('returns no attributes when the field has no error', () => {
    expect(fieldA11y('email', false)).toEqual({});
  });

  it('returns aria-invalid + aria-describedby pointing at the matching error id when errored', () => {
    expect(fieldA11y('email', true)).toEqual({
      'aria-invalid': true,
      'aria-describedby': 'email-error',
    });
  });

  it('never sets aria-invalid to false (absent instead of falsy)', () => {
    const result = fieldA11y('email', false);
    expect(result['aria-invalid']).toBeUndefined();
    expect('aria-invalid' in result).toBe(false);
  });
});
