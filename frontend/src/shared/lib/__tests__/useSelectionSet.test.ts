import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useSelectionSet } from '../useSelectionSet';

interface Row {
  id: string;
  dept: string;
}

const ROWS: Row[] = [
  { id: 'a', dept: 'X' },
  { id: 'b', dept: 'X' },
  { id: 'c', dept: 'Y' },
];

describe('useSelectionSet', () => {
  it('starts empty', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    expect(result.current.selected.size).toBe(0);
    expect(result.current.selectedCount).toBe(0);
    expect(result.current.allSelected).toBe(false);
    expect(result.current.isSelected('a')).toBe(false);
  });

  it('toggle(id, true) selects; toggle(id, false) clears', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    act(() => result.current.toggle('a', true));
    expect(result.current.isSelected('a')).toBe(true);
    expect(result.current.selectedCount).toBe(1);

    act(() => result.current.toggle('a', false));
    expect(result.current.isSelected('a')).toBe(false);
    expect(result.current.selectedCount).toBe(0);
  });

  it('toggle(id) without an explicit `on` flips the current state', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    act(() => result.current.toggle('a'));
    expect(result.current.isSelected('a')).toBe(true);
    act(() => result.current.toggle('a'));
    expect(result.current.isSelected('a')).toBe(false);
  });

  it('toggleAll selects every item when none/some are selected, and clears when all are selected', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    expect(result.current.allSelected).toBe(false);

    act(() => result.current.toggleAll());
    expect(result.current.allSelected).toBe(true);
    expect(result.current.selectedCount).toBe(3);
    expect(result.current.isSelected('a')).toBe(true);
    expect(result.current.isSelected('b')).toBe(true);
    expect(result.current.isSelected('c')).toBe(true);

    act(() => result.current.toggleAll());
    expect(result.current.allSelected).toBe(false);
    expect(result.current.selectedCount).toBe(0);
  });

  it('toggleAll with a partial selection selects the rest (does not clear)', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    act(() => result.current.toggle('a', true));
    act(() => result.current.toggleAll());
    expect(result.current.selectedCount).toBe(3);
  });

  it('clear empties the selection', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    act(() => result.current.toggleAll());
    act(() => result.current.clear());
    expect(result.current.selectedCount).toBe(0);
    expect(result.current.allSelected).toBe(false);
  });

  it('toggleMany / isAllSelected operate on an arbitrary subset (e.g. one group), independent of the full item list', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    const deptXIds = ROWS.filter((r) => r.dept === 'X').map((r) => r.id);

    expect(result.current.isAllSelected(deptXIds)).toBe(false);
    act(() => result.current.toggleMany(deptXIds));
    expect(result.current.isAllSelected(deptXIds)).toBe(true);
    expect(result.current.selectedCount).toBe(2);
    // The whole-list select-all is still false — only the X group is selected.
    expect(result.current.allSelected).toBe(false);
    expect(result.current.isSelected('c')).toBe(false);

    act(() => result.current.toggleMany(deptXIds));
    expect(result.current.selectedCount).toBe(0);
  });

  it('isAllSelected returns false for an empty id list', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    expect(result.current.isAllSelected([])).toBe(false);
  });

  it('setSelected is exposed as an escape hatch (e.g. keep only failed ids after a retry)', () => {
    const { result } = renderHook(() => useSelectionSet(ROWS, (r) => r.id));
    act(() => result.current.toggleAll());
    act(() => result.current.setSelected(new Set(['b'])));
    expect(result.current.selectedCount).toBe(1);
    expect(result.current.isSelected('b')).toBe(true);
    expect(result.current.isSelected('a')).toBe(false);
  });

  it('recomputes allSelected/toggleAll against a new `items` list', () => {
    const { result, rerender } = renderHook(
      ({ items }: { items: Row[] }) => useSelectionSet(items, (r) => r.id),
      { initialProps: { items: ROWS.slice(0, 2) } },
    );
    act(() => result.current.toggleAll());
    expect(result.current.allSelected).toBe(true);

    // A new (larger) item list — the previous selection no longer covers it.
    rerender({ items: ROWS });
    expect(result.current.allSelected).toBe(false);
    expect(result.current.selectedCount).toBe(2);
  });
});
