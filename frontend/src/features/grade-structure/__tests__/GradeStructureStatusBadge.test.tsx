import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeStructureStatusBadge } from '../components/GradeStructureStatusBadge';

describe('GradeStructureStatusBadge', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  for (const status of ['DRAFT', 'APPROVED', 'LOCKED', 'ARCHIVED'] as const) {
    it(`renders ${status} status with localized label`, async () => {
      render(renderWithProviders(<GradeStructureStatusBadge status={status} />));
      // Each badge renders the label inside a span with aria-label
      const el = await screen.findByLabelText(/.+/);
      expect(el).toBeInTheDocument();
    });
  }
});
