import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useDebouncedCallback, useDebouncedValue } from '../useDebounce';

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns the initial value immediately', () => {
    const { result } = renderHook(() => useDebouncedValue('a', 300));
    expect(result.current).toBe('a');
  });

  it('only updates after the delay has elapsed with no further change', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebouncedValue(value, 300),
      { initialProps: { value: 'a' } },
    );
    rerender({ value: 'b' });
    // Not yet — still the old value.
    expect(result.current).toBe('a');
    act(() => vi.advanceTimersByTime(299));
    expect(result.current).toBe('a');
    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toBe('b');
  });

  it('resets the timer on every intermediate change (only the LAST value lands)', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: number }) => useDebouncedValue(value, 300),
      { initialProps: { value: 1 } },
    );
    rerender({ value: 2 });
    act(() => vi.advanceTimersByTime(200));
    rerender({ value: 3 });
    act(() => vi.advanceTimersByTime(200));
    // 400ms since value=2 landed, but only 200ms since the latest (3) — not yet.
    expect(result.current).toBe(1);
    act(() => vi.advanceTimersByTime(100));
    expect(result.current).toBe(3);
  });

  it('clears its pending timer on unmount (no post-unmount setState)', () => {
    const { unmount } = renderHook(() => useDebouncedValue('a', 300));
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout');
    unmount();
    expect(clearSpy).toHaveBeenCalled();
  });
});

describe('useDebouncedCallback', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('coalesces rapid calls into one invocation after the delay', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 300));
    act(() => {
      result.current.run('x');
      result.current.run('y');
      result.current.run('z');
    });
    expect(fn).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(300));
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith('z');
  });

  it('cancel() stops the pending call from firing', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 300));
    act(() => result.current.run('x'));
    act(() => result.current.cancel());
    act(() => vi.advanceTimersByTime(500));
    expect(fn).not.toHaveBeenCalled();
  });

  it('runs unrelated `keyOf` channels independently (one key does not cancel another)', () => {
    const fn = vi.fn();
    const { result } = renderHook(() =>
      useDebouncedCallback(fn, 300, { keyOf: (key: string, _value: string) => key }),
    );
    act(() => result.current.run('q1', 'first'));
    act(() => vi.advanceTimersByTime(150));
    // A call on a DIFFERENT channel must not reset q1's timer.
    act(() => result.current.run('q2', 'second'));
    act(() => vi.advanceTimersByTime(150));
    // q1's 300ms has now elapsed (150 + 150); q2's has not (150ms in).
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith('q1', 'first');

    act(() => vi.advanceTimersByTime(150));
    expect(fn).toHaveBeenCalledTimes(2);
    expect(fn).toHaveBeenCalledWith('q2', 'second');
  });

  it('always invokes the LATEST callback closure (no stale closure bug)', () => {
    let seen: string | null = null;
    const { result, rerender } = renderHook(
      ({ label }: { label: string }) =>
        useDebouncedCallback(() => {
          seen = label;
        }, 300),
      { initialProps: { label: 'first' } },
    );
    act(() => result.current.run());
    rerender({ label: 'second' });
    act(() => vi.advanceTimersByTime(300));
    expect(seen).toBe('second');
  });

  it('clears ALL pending timers (every channel) on unmount', () => {
    const fn = vi.fn();
    const { result, unmount } = renderHook(() =>
      useDebouncedCallback(fn, 300, { keyOf: (key: string) => key }),
    );
    act(() => {
      result.current.run('q1');
      result.current.run('q2');
    });
    unmount();
    vi.advanceTimersByTime(500);
    expect(fn).not.toHaveBeenCalled();
  });
});
