import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ReportStatusBadge } from '../components/ReportStatusBadge';
import type { ReportStatus } from '../types';

const ALL: ReportStatus[] = [
  'REQUESTED',
  'QUEUED',
  'GENERATING',
  'GENERATED',
  'FAILED',
  'DOWNLOADED',
  'EXPIRED',
  'CANCELLED',
];

describe('ReportStatusBadge', () => {
  it.each(ALL)('renders icon + accessible label for %s', (status) => {
    const { container } = render(renderWithProviders(<ReportStatusBadge status={status} />));
    // StatusBadge renders the icon as an inline svg + an aria-label on the wrapper.
    expect(container.querySelector('svg')).not.toBeNull();
    const wrapper = container.querySelector('span[aria-label]');
    expect(wrapper).not.toBeNull();
    // Translation key must resolve to a non-empty label (not the raw key).
    const label = wrapper!.getAttribute('aria-label') ?? '';
    expect(label.length).toBeGreaterThan(0);
    expect(label).not.toContain('report.status.');
  });
});
