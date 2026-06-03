import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradePyramid } from '../components/GradePyramid';
import type { GradePyramidRow } from '../types';

function row(n: number, count: number): GradePyramidRow {
  return {
    grade_id: `g-${n}`,
    grade_number: n,
    grade_name: { 'ru-RU': `Грейд ${n}`, 'en-US': `Grade ${n}` },
    position_count: count,
    evaluation_count: count,
    min_score: (n - 1) * 100,
    max_score: n * 100,
  };
}

describe('GradePyramid', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders one row per grade and sorts high → low', () => {
    const rows = [row(1, 3), row(2, 5), row(3, 8)];
    render(renderWithProviders(<GradePyramid rows={rows} />));
    const figure = screen.getByTestId('grade-pyramid');
    const children = Array.from(figure.children) as HTMLElement[];
    expect(children).toHaveLength(3);
    expect(children[0].getAttribute('data-testid')).toBe('pyramid-row-3');
    expect(children[2].getAttribute('data-testid')).toBe('pyramid-row-1');
  });

  it('renders empty state when no rows are provided', () => {
    render(renderWithProviders(<GradePyramid rows={[]} />));
    expect(screen.getByTestId('grade-pyramid-empty')).toBeInTheDocument();
  });

  it('shows 0 placeholder text for zero-count rows', () => {
    const rows = [row(1, 0)];
    render(renderWithProviders(<GradePyramid rows={rows} />));
    const r = screen.getByTestId('pyramid-row-1');
    expect(r.getAttribute('data-position-count')).toBe('0');
    expect(r.textContent).toMatch(/0/);
  });
});
