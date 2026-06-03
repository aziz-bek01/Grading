import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ExportStatusBadge } from './ExportStatusBadge';
import type { ExportJobStatus } from '../types';

const ALL: ExportJobStatus[] = [
  'REQUESTED',
  'QUEUED',
  'GENERATING',
  'GENERATED',
  'FAILED',
  'DOWNLOADED',
  'EXPIRED',
  'CANCELLED',
];

describe('ExportStatusBadge', () => {
  it.each(ALL)('renders icon + label for %s', (status) => {
    const { container } = render(renderWithProviders(<ExportStatusBadge status={status} />));
    expect(container.querySelector('svg')).not.toBeNull();
    expect(container.querySelector('span')?.getAttribute('aria-label')).toBeTruthy();
  });
});
