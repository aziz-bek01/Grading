import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { FactorTable } from './FactorTable';
import type { Factor } from '../types';

function factor(code: string, weight: number, sort: number, withName = true): Factor {
  return {
    id: `f-${code}`,
    methodology_version_id: 'v1',
    code,
    name_i18n: withName
      ? { 'ru-RU': `Имя ${code}`, 'en-US': `Name ${code}`, 'uz-Cyrl-UZ': '', 'uz-Latn-UZ': '' }
      : {},
    weight,
    max_points: 100,
    sort_order: sort,
    required: true,
    levels: [{ id: `${code}-lvl-1`, factor_id: `f-${code}`, code: 'A', level_order: 0, points: 10, scale_value: 0.5, label_i18n: { 'ru-RU': 'A' } }],
  };
}

describe('FactorTable', () => {
  beforeAll(() => signIn('super-admin'));
  afterAll(() => signOut());

  it('renders one row per factor sorted by sort_order', () => {
    const factors = [factor('B', 50, 1), factor('A', 50, 0)];
    render(
      renderWithProviders(
        <FactorTable
          factors={factors}
          scoringMode="WEIGHTED_POINTS"
          currentLocale="ru-RU"
          onAdd={() => {}}
        />,
      ),
    );
    const rows = screen.getAllByTestId(/^factor-row-/);
    expect(rows.length).toBe(2);
    expect(rows[0].getAttribute('data-testid')).toBe('factor-row-A');
  });

  it('shows completeness dot for each locale (filled vs empty)', () => {
    const factors = [factor('A', 50, 0)];
    render(
      renderWithProviders(
        <FactorTable
          factors={factors}
          scoringMode="WEIGHTED_POINTS"
          currentLocale="ru-RU"
          onAdd={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('factor-A-locale-ru-RU-filled')).toBeInTheDocument();
    expect(screen.getByTestId('factor-A-locale-en-US-filled')).toBeInTheDocument();
    expect(screen.getByTestId('factor-A-locale-uz-Cyrl-UZ-empty')).toBeInTheDocument();
    expect(screen.getByTestId('factor-A-locale-uz-Latn-UZ-empty')).toBeInTheDocument();
  });

  it('reorder buttons fire onReorder with direction', () => {
    const factors = [factor('A', 50, 0), factor('B', 50, 1)];
    const onReorder = vi.fn();
    render(
      renderWithProviders(
        <FactorTable
          factors={factors}
          scoringMode="WEIGHTED_POINTS"
          currentLocale="ru-RU"
          onReorder={onReorder}
          onAdd={() => {}}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('factor-A-move-down'));
    expect(onReorder).toHaveBeenCalledWith(expect.objectContaining({ code: 'A' }), 'down');
  });

  it('hides Weight column in DIRECT_POINTS mode', () => {
    render(
      renderWithProviders(
        <FactorTable
          factors={[factor('A', 50, 0)]}
          scoringMode="DIRECT_POINTS"
          currentLocale="ru-RU"
          onAdd={() => {}}
        />,
      ),
    );
    // No "Weight" column header rendered when scoring mode is DIRECT_POINTS.
    expect(screen.queryByText(/weight/i)).toBeNull();
  });

  it('readOnly hides edit / reorder / remove controls', () => {
    render(
      renderWithProviders(
        <FactorTable
          factors={[factor('A', 50, 0)]}
          scoringMode="WEIGHTED_POINTS"
          currentLocale="ru-RU"
          readOnly
          onAdd={() => {}}
        />,
      ),
    );
    expect(screen.queryByTestId('factor-A-edit')).toBeNull();
    expect(screen.queryByTestId('factor-A-move-up')).toBeNull();
    expect(screen.queryByTestId('factor-add')).toBeNull();
  });
});
