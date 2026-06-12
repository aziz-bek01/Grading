import { describe, it, expect } from 'vitest';
import { shortId } from '../shortId';

describe('shortId', () => {
  it('returns the first UUID segment (8 chars) for a full UUID', () => {
    expect(shortId('40000000-0000-0000-0000-0000000000a2')).toBe('40000000');
  });

  it('never returns a 36-char string for a UUID input', () => {
    const out = shortId('50000000-0000-0000-0000-0000000000b3');
    expect(out.length).toBeLessThanOrEqual(8);
    expect(out).not.toContain('-');
  });

  it('truncates a long non-UUID id to 8 chars', () => {
    expect(shortId('abcdefghijklmnop')).toBe('abcdefgh');
  });

  it('leaves a short id unchanged', () => {
    expect(shortId('abc')).toBe('abc');
  });

  it('returns empty string for null / undefined / empty / whitespace', () => {
    expect(shortId(null)).toBe('');
    expect(shortId(undefined)).toBe('');
    expect(shortId('')).toBe('');
    expect(shortId('   ')).toBe('');
  });
});
