import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { AssignedGradeBadge } from '../components/AssignedGradeBadge';

describe('AssignedGradeBadge', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders grade number when assigned', () => {
    render(renderWithProviders(<AssignedGradeBadge gradeNumber={7} bandLabel="[600, 700]" />));
    expect(screen.getByTestId('assigned-grade-7')).toBeInTheDocument();
    expect(screen.getByTestId('assigned-grade-7').textContent).toMatch(/7/);
  });

  it('renders pending when no grade and not out-of-range', () => {
    render(renderWithProviders(<AssignedGradeBadge gradeNumber={null} />));
    expect(screen.getByTestId('assigned-grade-pending')).toBeInTheDocument();
  });

  it('renders out-of-range when flagged', () => {
    render(
      renderWithProviders(
        <AssignedGradeBadge gradeNumber={null} outOfRange />,
      ),
    );
    expect(screen.getByTestId('assigned-grade-out-of-range')).toBeInTheDocument();
  });
});
