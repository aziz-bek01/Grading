import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { FactorTabs } from './FactorTabs';
import { BY_FACTOR_STICKY_TOP, BY_FACTOR_STICKY_Z } from './stickyOffset';
import type { Factor } from '@/features/methodology/types';

function makeFactor(code: string, order: number): Factor {
  return {
    id: `f-${code}`,
    methodology_version_id: 'mv-1',
    code,
    name_i18n: { 'ru-RU': code, 'en-US': code },
    weight: 10,
    max_points: 100,
    sort_order: order,
    required: true,
    levels: [],
  };
}

/**
 * FactorTabs is the public surface of the by-factor view (the page-level
 * smoke is covered by PositionScoreRow + BulkScoreDialog at the unit level).
 * The old right-side RubricPanel was RETIRED in the K-sheet redesign — the
 * `rubric panel is retired (AC-1)` block below guards that the rubric DOM no
 * longer exists in the evaluator view. This test guards the URL/state
 * contract the parent (EvaluationListPage) relies on.
 */
describe('FactorTabs (by-factor view contract)', () => {
  it('renders factor tabs sorted by sort_order', () => {
    const factors = [
      makeFactor('K2', 1),
      makeFactor('K1', 0),
      makeFactor('K3', 2),
    ];
    render(
      renderWithProviders(
        <FactorTabs
          factors={factors}
          activeFactorId="f-K1"
          onSelect={() => {}}
        />,
      ),
    );
    const tabs = screen.getAllByRole('tab');
    expect(tabs.map((t) => t.getAttribute('data-testid'))).toEqual([
      'factor-tab-K1',
      'factor-tab-K2',
      'factor-tab-K3',
    ]);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[1].getAttribute('aria-selected')).toBe('false');
  });

  it('emits onSelect with the clicked factor id', () => {
    const onSelect = vi.fn();
    const factors = [makeFactor('K1', 0), makeFactor('K2', 1)];
    render(
      renderWithProviders(
        <FactorTabs
          factors={factors}
          activeFactorId="f-K1"
          onSelect={onSelect}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('factor-tab-K2'));
    expect(onSelect).toHaveBeenCalledWith('f-K2');
  });

  it('renders the completion indicator per state', () => {
    const factors = [
      makeFactor('K1', 0),
      makeFactor('K2', 1),
      makeFactor('K3', 2),
    ];
    render(
      renderWithProviders(
        <FactorTabs
          factors={factors}
          activeFactorId="f-K1"
          completion={{
            'f-K1': 'full',
            'f-K2': 'partial',
            'f-K3': 'empty',
          }}
          onSelect={() => {}}
        />,
      ),
    );
    expect(
      screen.getByTestId('factor-tab-state-K1').getAttribute('data-state'),
    ).toBe('full');
    expect(
      screen.getByTestId('factor-tab-state-K2').getAttribute('data-state'),
    ).toBe('partial');
    expect(
      screen.getByTestId('factor-tab-state-K3').getAttribute('data-state'),
    ).toBe('empty');
  });

  it('forwards a parent-supplied sticky className to the tab strip', () => {
    // The parent (EvaluationByFactorView) applies the SHARED sticky offset so
    // the tabs and the rubric never diverge. Guard that FactorTabs renders the
    // className it is given onto the tablist root.
    const factors = [makeFactor('K1', 0)];
    render(
      renderWithProviders(
        <FactorTabs
          className={`sticky ${BY_FACTOR_STICKY_TOP} ${BY_FACTOR_STICKY_Z}`}
          factors={factors}
          activeFactorId="f-K1"
          onSelect={() => {}}
        />,
      ),
    );
    const tablist = screen.getByTestId('factor-tabs');
    expect(tablist.className).toContain('sticky');
    expect(tablist.className).toContain(BY_FACTOR_STICKY_TOP);
    expect(tablist.className).toContain(BY_FACTOR_STICKY_Z);
  });
});

/**
 * AC-1: the right-side rubric reference panel is GONE from the K-sheet.
 * The redesign retired RubricPanel.tsx entirely (no module to import, no
 * rubric DOM rendered for evaluators) — each level's description now lives
 * in-line in the per-row LevelDropSelect. This guard fails the build if the
 * panel (or its module) is ever reintroduced into the by-factor tree.
 */
describe('rubric panel is retired (AC-1)', () => {
  it('has no RubricPanel module in the by-factor component tree', () => {
    const modules = import.meta.glob('./*.tsx');
    expect(Object.keys(modules)).not.toContain('./RubricPanel.tsx');
  });
});

/**
 * Shared sticky-offset contract — the single source of truth the FactorTabs
 * strip imports for its sticky offset. These assertions encode the TopBar
 * geometry so a future TopBar resize forces an intentional update here (the
 * previous bug was the tabs using top-14 / 56px while a now-retired sibling
 * used top-20 / 80px, tucking the tabs ~6px under the 62px header).
 */
describe('by-factor sticky-offset constant', () => {
  it('clears the 62px TopBar (h-14 + h-1.5) with top-20 / 80px', () => {
    // top-20 === 5rem === 80px > 62px TopBar height.
    expect(BY_FACTOR_STICKY_TOP).toBe('top-20');
  });

  it('paints below the TopBar (TopBar is z-20, in-page sticky is z-10)', () => {
    expect(BY_FACTOR_STICKY_Z).toBe('z-10');
  });
});
