import { useCallback, useMemo, useState, type Dispatch, type SetStateAction } from 'react';

export interface UseSelectionSetResult<Id> {
  /** The current selection (a fresh `Set` on every change, never mutated in place). */
  selected: Set<Id>;
  /** `selected.size` — convenience for toolbar counters / disabled-state checks. */
  selectedCount: number;
  isSelected: (id: Id) => boolean;
  /** Flips one id. `on` mirrors a checkbox's `checked` — omit to just toggle. */
  toggle: (id: Id, on?: boolean) => void;
  /**
   * Selects every id in `ids` when they are not ALL already selected; clears
   * them when they are (the "select-all" checkbox semantics). Works on any
   * subset — pass the full row list for a page-level select-all, or a
   * smaller group (e.g. one department's rows) for a group-level select-all.
   */
  toggleMany: (ids: Iterable<Id>) => void;
  /** True when every id in `ids` is currently selected (`false` for an empty list). */
  isAllSelected: (ids: Iterable<Id>) => boolean;
  /**
   * `toggleMany` bound to the hook's own `items` (via `getId`) — the common
   * "select all rows currently on screen" checkbox.
   */
  toggleAll: () => void;
  /** `isAllSelected` for the hook's own `items` — drives the select-all checkbox's checked state. */
  allSelected: boolean;
  /** Empties the selection. */
  clear: () => void;
  /** Escape hatch for callers that need to replace the whole selection (e.g. keep only the rows that failed a bulk action after a retry). */
  setSelected: Dispatch<SetStateAction<Set<Id>>>;
}

/**
 * Shared row-selection state: select-all / toggle-one / clear, backed by a
 * `Set`. Extracted from three components that hand-rolled the identical
 * `Set`-based logic (AddPositionsDialog, OpenPanelDialog,
 * EvaluationByFactorView) — see FE-039.
 *
 * `items` is the current page/list of selectable rows and `getId` extracts
 * each row's id; `toggleAll` / `allSelected` are derived from them. Nested
 * group-level "select all" (e.g. one department's rows inside a larger
 * candidate list) can reuse the same selection via `toggleMany` /
 * `isAllSelected` with a different id subset — no second hook instance or
 * copy of the toggle logic needed.
 */
export function useSelectionSet<T, Id = string>(
  items: readonly T[],
  getId: (item: T) => Id,
): UseSelectionSetResult<Id> {
  const [selected, setSelected] = useState<Set<Id>>(() => new Set());

  const isSelected = useCallback((id: Id) => selected.has(id), [selected]);

  const toggle = useCallback((id: Id, on?: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      const shouldSelect = on ?? !prev.has(id);
      if (shouldSelect) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);

  const isAllSelected = useCallback(
    (ids: Iterable<Id>) => {
      let any = false;
      for (const id of ids) {
        any = true;
        if (!selected.has(id)) return false;
      }
      return any;
    },
    [selected],
  );

  const toggleMany = useCallback(
    (ids: Iterable<Id>) => {
      const idList = Array.from(ids);
      setSelected((prev) => {
        const allOn = idList.length > 0 && idList.every((id) => prev.has(id));
        const next = new Set(prev);
        if (allOn) {
          for (const id of idList) next.delete(id);
        } else {
          for (const id of idList) next.add(id);
        }
        return next;
      });
    },
    [],
  );

  const itemIds = useMemo(() => items.map(getId), [items, getId]);
  const allSelected = isAllSelected(itemIds);
  const toggleAll = useCallback(() => toggleMany(itemIds), [toggleMany, itemIds]);
  const clear = useCallback(() => setSelected(new Set()), []);

  return {
    selected,
    selectedCount: selected.size,
    isSelected,
    toggle,
    toggleMany,
    isAllSelected,
    toggleAll,
    allSelected,
    clear,
    setSelected,
  };
}
