import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuditLogStatCard } from './AuditLogStatCard';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

describe('<AuditLogStatCard />', () => {
  beforeEach(() => {
    signOut();
  });

  it('is hidden for a Viewer (no AUDIT_READ)', () => {
    signIn('viewer');
    render(renderWithProviders(<AuditLogStatCard />));
    expect(screen.queryByTestId('audit-log-stat-card')).not.toBeInTheDocument();
  });

  it('is visible for a Super Admin (has AUDIT_READ)', () => {
    signIn('super-admin');
    render(renderWithProviders(<AuditLogStatCard />));
    expect(screen.getByTestId('audit-log-stat-card')).toBeInTheDocument();
  });
});
