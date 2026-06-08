import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { UserStatusBadge } from './UserStatusBadge';
import { renderWithProviders } from '@/test/testUtils';
import type { MembershipStatus, UserStatus } from '../types/userTypes';

/**
 * Asserts the badge resolves a localized label (never leaks the raw i18n key)
 * for every USER and MEMBERSHIP status — including the new DISABLED / LOCKED
 * user statuses and the SUSPENDED membership status that the enum-drift fix
 * introduced.
 */
function singleBadgeText(status: UserStatus | MembershipStatus): string {
  render(renderWithProviders(<UserStatusBadge status={status} />));
  // StatusBadge sets the resolved label as aria-label on the pill.
  const badge = screen.getByLabelText((label) => label.length > 0 && !label.includes('users.status'));
  return badge.getAttribute('aria-label') ?? '';
}

describe('<UserStatusBadge />', () => {
  it.each(['ACTIVE', 'INVITED', 'DISABLED', 'LOCKED'] as const)(
    'renders a localized label for user status %s',
    (status) => {
      const label = singleBadgeText(status);
      expect(label.length).toBeGreaterThan(0);
      // Never the raw enum value nor the i18n key.
      expect(label).not.toBe(status);
      expect(label).not.toContain('users.status');
    },
  );

  it.each(['SUSPENDED', 'REVOKED'] as const)(
    'renders a localized label for membership status %s',
    (status) => {
      const label = singleBadgeText(status);
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toBe(status);
      expect(label).not.toContain('users.status');
    },
  );
});
