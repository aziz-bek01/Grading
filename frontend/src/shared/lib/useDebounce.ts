import { useCallback, useEffect, useRef, useState } from 'react';

/** Internal channel key used when a caller does not supply `keyOf`. */
const DEFAULT_CHANNEL = '__default__';

/**
 * Debounces a rapidly-changing VALUE: returns a copy of `value` that only
 * updates once `delayMs` has elapsed with no further change. Use this for
 * "wait for the user to stop typing/clicking, then derive something from
 * the value" (e.g. a score-to-grade preview lookup keyed off a text input).
 *
 * The pending timer is cleared on every re-schedule AND on unmount, so a
 * stale timeout can never call `setState` after the value has moved on or
 * the component is gone.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}

export interface UseDebouncedCallbackOptions<Args extends unknown[]> {
  /**
   * Resolves the debounce CHANNEL for a call from its arguments, so unrelated
   * calls run on independent timers instead of cancelling each other (e.g.
   * per-row/per-field autosave — editing field B should not swallow a
   * still-pending save for field A). Omit for a single shared channel, where
   * every call debounces against the previous one (the common case).
   */
  keyOf?: (...args: Args) => string;
}

export interface UseDebouncedCallbackResult<Args extends unknown[]> {
  /** Schedules `callback` to run `delayMs` after the last call on its channel. */
  run: (...args: Args) => void;
  /** Cancels the pending timer for one channel (the default channel when omitted). */
  cancel: (key?: string) => void;
}

/**
 * Debounces a CALLBACK invocation. All pending timers (across every channel)
 * are cleared on unmount — the one piece every hand-rolled version of this
 * needs and the one a previous implementation (`QuestionnairePage`'s
 * per-question autosave) was missing, which ESLint flagged
 * (`react-hooks/exhaustive-deps`, stale-ref-in-cleanup warning). Because the
 * cleanup lives here once, every caller gets it for free.
 */
export function useDebouncedCallback<Args extends unknown[]>(
  callback: (...args: Args) => void,
  delayMs: number,
  options?: UseDebouncedCallbackOptions<Args>,
): UseDebouncedCallbackResult<Args> {
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  // Latest-ref pattern: keep the current callback/keyOf/delay available to
  // already-scheduled timers without forcing `run`/`cancel` to be re-created
  // (and without a stale closure ever firing). Synced in an effect (not
  // during render) so a scheduled timer always reads a committed value.
  const callbackRef = useRef(callback);
  const keyOfRef = useRef(options?.keyOf);
  const delayRef = useRef(delayMs);
  useEffect(() => {
    callbackRef.current = callback;
    keyOfRef.current = options?.keyOf;
    delayRef.current = delayMs;
  });

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
    };
  }, []);

  const cancel = useCallback((key: string = DEFAULT_CHANNEL) => {
    const timer = timersRef.current.get(key);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(key);
    }
  }, []);

  const run = useCallback((...args: Args) => {
    const key = keyOfRef.current ? keyOfRef.current(...args) : DEFAULT_CHANNEL;
    const existing = timersRef.current.get(key);
    if (existing) clearTimeout(existing);
    const timer = setTimeout(() => {
      timersRef.current.delete(key);
      callbackRef.current(...args);
    }, delayRef.current);
    timersRef.current.set(key, timer);
  }, []);

  return { run, cancel };
}
