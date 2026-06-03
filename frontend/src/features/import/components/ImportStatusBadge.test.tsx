import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ImportStatusBadge } from './ImportStatusBadge';
import type { ImportBatchStatus } from '../types';

const ALL: ImportBatchStatus[] = [
  'UPLOADED',
  'SCANNING',
  'SCAN_FAILED',
  'PARSING',
  'VALIDATING',
  'VALIDATION_FAILED',
  'READY_FOR_REVIEW',
  'READY_TO_COMMIT',
  'COMMITTING',
  'COMMITTED',
  'PARTIALLY_COMMITTED',
  'FAILED',
  'CANCELLED',
  'ARCHIVED',
];

describe('ImportStatusBadge', () => {
  it.each(ALL)('renders icon + localized label for %s', (status) => {
    const { container } = render(renderWithProviders(<ImportStatusBadge status={status} />));
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    const span = container.querySelector('span');
    expect(span?.getAttribute('aria-label')).toBeTruthy();
  });
});
