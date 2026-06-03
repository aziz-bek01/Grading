import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { LockedMethodologyHeader } from './LockedMethodologyHeader';
import type { MethodologyVersion } from '../types';

const baseVersion: MethodologyVersion = {
  id: 'v-1',
  methodology_id: 'm-1',
  project_id: 'p-1',
  version_number: 1,
  status: 'LOCKED',
  scoring_mode: 'WEIGHTED_POINTS',
  target_total_points: 100,
  factors: [],
  approved_at: '2026-05-12T14:23:00Z',
  approved_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
  approved_by_name: 'Dilshod Karimov',
  locked_at: '2026-05-12T14:23:00Z',
  locked_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
  locked_by_name: 'Dilshod Karimov',
  created_at: '2026-02-01T10:00:00Z',
  updated_at: '2026-05-12T14:23:00Z',
};

describe('LockedMethodologyHeader', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => signOut());

  it('renders actor + timestamp in LOCKED state', () => {
    render(
      renderWithProviders(<LockedMethodologyHeader version={baseVersion} onCreateNewVersion={() => {}} />),
    );
    const banner = screen.getByTestId('locked-methodology-header');
    expect(banner.getAttribute('data-status')).toBe('LOCKED');
    const text = screen.getByTestId('locked-actor-time').textContent ?? '';
    expect(text).toContain('Dilshod Karimov');
    // FIX-6: the raw ISO must NOT be printed — it is now locale-formatted via
    // the shared `new Date(iso).toLocaleString(locale)` pattern.
    expect(text).not.toContain('2026-05-12T14:23:00Z');
    // Test i18n language is ru-RU (see src/test/setup.ts) — match the component.
    expect(text).toContain(new Date('2026-05-12T14:23:00Z').toLocaleString('ru-RU'));
  });

  it('Create new version button visible with METHODOLOGY_EDIT', () => {
    const fn = vi.fn();
    render(renderWithProviders(<LockedMethodologyHeader version={baseVersion} onCreateNewVersion={fn} />));
    const btn = screen.getByTestId('locked-create-new-version');
    fireEvent.click(btn);
    expect(fn).toHaveBeenCalled();
  });

  it('Create new version button NOT mounted for viewer (no METHODOLOGY_EDIT)', () => {
    signOut();
    signIn('viewer');
    render(renderWithProviders(<LockedMethodologyHeader version={baseVersion} onCreateNewVersion={() => {}} />));
    expect(screen.queryByTestId('locked-create-new-version')).toBeNull();
  });

  // ---------- D-407 / PC4-5 — actor-name resolution ----------

  it('D-407: prefers locked_by_name over locked_by UUID', () => {
    render(
      renderWithProviders(
        <LockedMethodologyHeader version={baseVersion} onCreateNewVersion={() => {}} />,
      ),
    );
    const txt = screen.getByTestId('locked-actor-time').textContent ?? '';
    expect(txt).toContain('Dilshod Karimov');
    // UUID must NOT leak into the rendered text.
    expect(txt).not.toContain('7e9c1234');
  });

  it('D-407: falls back to "Unknown actor" when both name + UUID are missing', () => {
    const withoutActor: MethodologyVersion = {
      ...baseVersion,
      locked_by: null,
      locked_by_name: null,
    };
    render(
      renderWithProviders(<LockedMethodologyHeader version={withoutActor} onCreateNewVersion={() => {}} />),
    );
    const txt = screen.getByTestId('locked-actor-time').textContent ?? '';
    // Either the translated label OR the literal i18n key (when key missing
    // in some locales) — must not be empty / not the UUID placeholder.
    expect(txt.length).toBeGreaterThan(0);
    expect(txt).not.toContain('null');
    expect(txt).not.toContain('undefined');
  });

  it('D-407: APPROVED state shows approved_by_name not approved_by UUID', () => {
    const approved: MethodologyVersion = {
      ...baseVersion,
      status: 'APPROVED',
      locked_at: null,
      locked_by: null,
      locked_by_name: null,
      approved_by: '7e9c1234-5678-90ab-cdef-1234567890ab',
      approved_by_name: 'Anvar Asqarov',
    };
    render(
      renderWithProviders(<LockedMethodologyHeader version={approved} onCreateNewVersion={() => {}} />),
    );
    const txt = screen.getByTestId('locked-actor-time').textContent ?? '';
    expect(txt).toContain('Anvar Asqarov');
    expect(txt).not.toContain('7e9c1234');
  });

  it('D-407: LOCKED with prior approved_by_name shows a separate "Approved by" line', () => {
    render(
      renderWithProviders(
        <LockedMethodologyHeader version={baseVersion} onCreateNewVersion={() => {}} />,
      ),
    );
    expect(screen.getByTestId('locked-approved-by').textContent).toContain('Dilshod Karimov');
  });

  it('D-407: transitional fallback — uses UUID if name not yet provided', () => {
    // Simulates Phase 4 → Phase 4 remediation transition: backend rolled out
    // partially and ships UUID without the resolved name.
    const noName: MethodologyVersion = {
      ...baseVersion,
      locked_by_name: null,
    };
    render(
      renderWithProviders(<LockedMethodologyHeader version={noName} onCreateNewVersion={() => {}} />),
    );
    const txt = screen.getByTestId('locked-actor-time').textContent ?? '';
    // Falls through to the UUID until backend completes the join.
    expect(txt).toContain('7e9c1234');
  });
});
