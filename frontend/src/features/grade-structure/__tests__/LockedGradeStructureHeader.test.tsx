import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { LockedGradeStructureHeader } from '../components/LockedGradeStructureHeader';
import type { GradeStructure } from '../types';

function structure(overrides: Partial<GradeStructure> = {}): GradeStructure {
  return {
    id: 'gs-x',
    project_id: null,
    code: 'TEST-14',
    name: { 'ru-RU': 'Тест 14', 'en-US': 'Test 14' },
    structure_type: 'GRADE_14',
    status: 'APPROVED',
    gap_policy: 'STRICT_NO_GAPS',
    version_number: 1,
    approved_at: '2026-04-22T10:00:00Z',
    approved_by_name: 'Test Approver',
    created_at: '2026-04-01T10:00:00Z',
    updated_at: '2026-04-22T10:00:00Z',
    grades: [],
    ...overrides,
  };
}

describe('LockedGradeStructureHeader', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders approved-by line on APPROVED', () => {
    render(
      renderWithProviders(<LockedGradeStructureHeader structure={structure()} />),
    );
    const root = screen.getByTestId('locked-grade-structure-header');
    expect(root.getAttribute('data-status')).toBe('APPROVED');
    expect(screen.getByTestId('locked-actor-time').textContent).toMatch(
      /Test Approver/,
    );
  });

  it('renders locked-by + approved-by lines on LOCKED', () => {
    render(
      renderWithProviders(
        <LockedGradeStructureHeader
          structure={structure({
            status: 'LOCKED',
            locked_at: '2026-04-25T11:00:00Z',
            locked_by_name: 'Test Locker',
          })}
        />,
      ),
    );
    const root = screen.getByTestId('locked-grade-structure-header');
    expect(root.getAttribute('data-status')).toBe('LOCKED');
    expect(screen.getByTestId('locked-actor-time').textContent).toMatch(
      /Test Locker/,
    );
    // Both APPROVED and LOCKED metadata present → secondary "approved-by" line
    expect(screen.getByTestId('locked-approved-by').textContent).toMatch(
      /Test Approver/,
    );
  });

  it('calls onCreateNewVersion when CTA is clicked', () => {
    const onCreate = vi.fn();
    render(
      renderWithProviders(
        <LockedGradeStructureHeader
          structure={structure()}
          onCreateNewVersion={onCreate}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('grade-structure-create-new-version'));
    expect(onCreate).toHaveBeenCalled();
  });
});
