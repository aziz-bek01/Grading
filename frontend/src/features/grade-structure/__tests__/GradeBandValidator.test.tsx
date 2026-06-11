import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeBandValidator } from '../components/GradeBandValidator';
import type { Grade } from '../types';

function grade(n: number, min: number, max: number): Grade {
  return {
    id: `g-${n}`,
    grade_structure_id: 's',
    grade_number: n,
    name_i18n: { 'ru-RU': `Грейд ${n}` },
    sort_order: n - 1,
    band: {
      id: `g-${n}-band`,
      grade_id: `g-${n}`,
      min_score: min,
      max_score: max,
    },
  };
}

describe('GradeBandValidator', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders green when all bands are valid', () => {
    const grades = [grade(1, 0, 50), grade(2, 50.0001, 100)];
    render(
      renderWithProviders(
        <GradeBandValidator grades={grades} gapPolicy="STRICT_NO_GAPS" />,
      ),
    );
    const root = screen.getByTestId('grade-band-validator');
    expect(root.getAttribute('data-state')).toBe('ok');
  });

  it('renders red when an overlap exists (regardless of gap policy)', () => {
    const grades = [grade(1, 0, 105), grade(2, 100, 200)];
    render(
      renderWithProviders(
        <GradeBandValidator grades={grades} gapPolicy="ALLOW_GAPS_WARN" />,
      ),
    );
    const root = screen.getByTestId('grade-band-validator');
    expect(root.getAttribute('data-state')).toBe('error');
    expect(screen.getByTestId('validator-overlap-line')).toBeInTheDocument();
  });

  it('renders yellow when only a gap exists under ALLOW_GAPS_WARN', () => {
    const grades = [grade(1, 0, 50), grade(2, 100, 200)];
    render(
      renderWithProviders(
        <GradeBandValidator grades={grades} gapPolicy="ALLOW_GAPS_WARN" />,
      ),
    );
    const root = screen.getByTestId('grade-band-validator');
    expect(root.getAttribute('data-state')).toBe('warn');
    expect(screen.getByTestId('validator-gap-line')).toBeInTheDocument();
  });

  it('renders red when a gap exists under STRICT_NO_GAPS', () => {
    const grades = [grade(1, 0, 50), grade(2, 100, 200)];
    render(
      renderWithProviders(
        <GradeBandValidator grades={grades} gapPolicy="STRICT_NO_GAPS" />,
      ),
    );
    const root = screen.getByTestId('grade-band-validator');
    expect(root.getAttribute('data-state')).toBe('error');
  });

  it('renders empty state when no grades are passed', () => {
    render(
      renderWithProviders(
        <GradeBandValidator grades={[]} gapPolicy="STRICT_NO_GAPS" />,
      ),
    );
    const root = screen.getByTestId('grade-band-validator');
    expect(root.getAttribute('data-state')).toBe('empty');
  });
});
