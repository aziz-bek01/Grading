import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeStructureTypeBadge } from '../components/GradeStructureTypeBadge';

describe('GradeStructureTypeBadge', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  for (const type of ['GRADE_14', 'GRADE_16', 'CUSTOM'] as const) {
    it(`renders ${type}`, async () => {
      render(renderWithProviders(<GradeStructureTypeBadge type={type} />));
      const el = await screen.findByTestId(`grade-structure-type-${type}`);
      expect(el).toBeInTheDocument();
      expect(el.textContent?.trim().length ?? 0).toBeGreaterThan(0);
    });
  }
});
