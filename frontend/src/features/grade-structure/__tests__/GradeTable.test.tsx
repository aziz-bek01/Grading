import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeTable } from '../components/GradeTable';
import type { Grade } from '../types';

function grade(n: number, min: number, max: number): Grade {
  return {
    id: `g-${n}`,
    grade_structure_id: 's',
    grade_number: n,
    name_i18n: { 'ru-RU': `Грейд ${n}`, 'en-US': `Grade ${n}` },
    sort_order: n - 1,
    band: {
      id: `g-${n}-band`,
      grade_id: `g-${n}`,
      min_score: min,
      max_score: max,
    },
  };
}

describe('GradeTable', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders one row per grade with name + min/max', () => {
    const grades = [grade(1, 0, 50), grade(2, 50.0001, 100)];
    render(
      renderWithProviders(
        <GradeTable grades={grades} currentLocale="ru-RU" readOnly={false} />,
      ),
    );
    expect(screen.getByTestId('grade-row-1')).toBeInTheDocument();
    expect(screen.getByTestId('grade-row-2')).toBeInTheDocument();
    expect(screen.getByText('Грейд 1')).toBeInTheDocument();
  });

  it('shows actions when not read-only and hides them when read-only', () => {
    const grades = [grade(1, 0, 50)];
    const { rerender } = render(
      renderWithProviders(
        <GradeTable grades={grades} currentLocale="ru-RU" readOnly={false} />,
      ),
    );
    expect(screen.getByTestId('grade-1-edit')).toBeInTheDocument();
    expect(screen.getByTestId('grade-1-remove')).toBeInTheDocument();

    rerender(
      renderWithProviders(
        <GradeTable grades={grades} currentLocale="ru-RU" readOnly />,
      ),
    );
    expect(screen.queryByTestId('grade-1-edit')).not.toBeInTheDocument();
  });

  it('calls onEdit and onRemove handlers', () => {
    const grades = [grade(1, 0, 50)];
    const onEdit = vi.fn();
    const onRemove = vi.fn();
    render(
      renderWithProviders(
        <GradeTable
          grades={grades}
          currentLocale="ru-RU"
          readOnly={false}
          onEdit={onEdit}
          onRemove={onRemove}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('grade-1-edit'));
    fireEvent.click(screen.getByTestId('grade-1-remove'));
    expect(onEdit).toHaveBeenCalledWith(grades[0]);
    expect(onRemove).toHaveBeenCalledWith(grades[0]);
  });

  it('renders move up/down controls and calls onMove with direction (FE-7)', () => {
    const grades = [grade(1, 0, 50), grade(2, 50.0001, 100), grade(3, 100.0001, 150)];
    const onMove = vi.fn();
    render(
      renderWithProviders(
        <GradeTable
          grades={grades}
          currentLocale="ru-RU"
          readOnly={false}
          onMove={onMove}
        />,
      ),
    );
    // First row's "up" is disabled, last row's "down" is disabled.
    expect((screen.getByTestId('grade-1-move-up') as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId('grade-3-move-down') as HTMLButtonElement).disabled).toBe(
      true,
    );
    fireEvent.click(screen.getByTestId('grade-2-move-up'));
    expect(onMove).toHaveBeenCalledWith(grades[1], 'up');
    fireEvent.click(screen.getByTestId('grade-2-move-down'));
    expect(onMove).toHaveBeenCalledWith(grades[1], 'down');
  });

  it('hides move controls when onMove is not provided', () => {
    const grades = [grade(1, 0, 50)];
    render(
      renderWithProviders(
        <GradeTable grades={grades} currentLocale="ru-RU" readOnly={false} />,
      ),
    );
    expect(screen.queryByTestId('grade-1-move-up')).not.toBeInTheDocument();
  });

  it('marks an overlapping pair in red and a gap pair in yellow', () => {
    const grades: Grade[] = [
      grade(1, 0, 105),
      grade(2, 100, 200), // overlap with G1 at [100,105]
      grade(3, 300, 350), // gap between G2 (max 200) and G3 (min 300)
    ];
    render(
      renderWithProviders(
        <GradeTable grades={grades} currentLocale="ru-RU" readOnly={false} />,
      ),
    );
    expect(screen.getByTestId('grade-1-validation').textContent).toMatch(
      /Пересечение/i,
    );
    expect(screen.getByTestId('grade-2-validation').textContent).toMatch(
      /Пересечение|Разрыв/i,
    );
    expect(screen.getByTestId('grade-3-validation').textContent).toMatch(/Разрыв/i);
  });
});
