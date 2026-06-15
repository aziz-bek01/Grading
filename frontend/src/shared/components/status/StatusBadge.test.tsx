import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { StatusBadge } from './StatusBadge';
import { resolveStatusSpec } from './statusSpec';
import { ProjectStatusBadge } from '@/features/projects/components/ProjectStatusBadge';
import type { ProjectStatus } from '@/features/projects/types/projectTypes';

describe('resolveStatusSpec', () => {
  type Demo = 'ACTIVE' | 'WHO_KNOWS';
  const map: Partial<Record<Demo, { tone: 'approved'; label: string }>> = {
    ACTIVE: { tone: 'approved', label: 'Active' },
  };

  it('returns the mapped spec for a known status', () => {
    expect(resolveStatusSpec<Demo>(map, 'ACTIVE')).toEqual({ tone: 'approved', label: 'Active' });
  });

  it('falls back to a neutral tone + the raw status for an unknown status', () => {
    expect(resolveStatusSpec<Demo>(map, 'WHO_KNOWS')).toEqual({ tone: 'draft', label: 'WHO_KNOWS' });
  });
});

describe('StatusBadge unknown tone', () => {
  it('does not throw on an unmapped tone and still renders a label', () => {
    const { container } = render(
      // @ts-expect-error — deliberately pass an out-of-range tone value.
      renderWithProviders(<StatusBadge tone="totally-unknown" label="Mystery" />),
    );
    expect(container.querySelector('span')?.getAttribute('aria-label')).toBe('Mystery');
  });
});

describe('per-entity badge unknown status (ProjectStatusBadge)', () => {
  it('renders a neutral badge with the raw status instead of throwing', () => {
    const { container } = render(
      // Simulate a new/stale backend enum the FE does not know about.
      renderWithProviders(<ProjectStatusBadge status={'FUTURE_STATE' as ProjectStatus} />),
    );
    const span = container.querySelector('span[aria-label]');
    expect(span?.getAttribute('aria-label')).toBe('FUTURE_STATE');
  });
});
