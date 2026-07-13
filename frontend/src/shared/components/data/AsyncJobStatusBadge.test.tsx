import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { AsyncJobStatusBadge } from './AsyncJobStatusBadge';
import type { StatusTone } from '@/shared/components/status/StatusBadge';

type DemoStatus = 'GENERATED' | 'FAILED' | 'NOT_IN_MAP';

// Reuses the real `export.status.*` i18n keys so the resolved label is
// verified against actual translated copy, not a synthetic fixture.
const TONE_MAP: Record<DemoStatus, StatusTone> = {
  GENERATED: 'approved',
  FAILED: 'needs-attention',
} as Record<DemoStatus, StatusTone>;

describe('AsyncJobStatusBadge', () => {
  it('renders the mapped tone + translated label for a known status', () => {
    const { container } = render(
      renderWithProviders(
        <AsyncJobStatusBadge status="GENERATED" toneMap={TONE_MAP} labelPrefix="export.status" />,
      ),
    );
    const span = container.querySelector('span[aria-label]');
    expect(span).not.toBeNull();
    // export.status.GENERATED === 'Готов' (ru-RU default in tests).
    expect(span!.getAttribute('aria-label')).toBe('Готов');
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('falls back to the neutral "draft" tone (never throws) for a status missing from toneMap', () => {
    const { container } = render(
      renderWithProviders(
        <AsyncJobStatusBadge
          status={'NOT_IN_MAP' as DemoStatus}
          toneMap={TONE_MAP}
          labelPrefix="export.status"
        />,
      ),
    );
    const span = container.querySelector('span[aria-label]');
    expect(span).not.toBeNull();
    // No i18n entry for NOT_IN_MAP — i18next returns the raw key, proving the
    // resolver ran (and did not throw) rather than silently rendering blank.
    expect(span!.getAttribute('aria-label')).toBe('export.status.NOT_IN_MAP');
  });

  it('applies a different labelPrefix independently (report.status.* namespace)', () => {
    const { container } = render(
      renderWithProviders(
        <AsyncJobStatusBadge status="FAILED" toneMap={TONE_MAP} labelPrefix="report.status" />,
      ),
    );
    const span = container.querySelector('span[aria-label]');
    // report.status.FAILED === 'Не удалось' in ru-RU.
    expect(span!.getAttribute('aria-label')).toBe('Не удалось');
  });
});
