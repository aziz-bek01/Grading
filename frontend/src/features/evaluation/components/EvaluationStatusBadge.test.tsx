import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { EvaluationStatusBadge } from './EvaluationStatusBadge';
import type { EvaluationStatus } from '../types';

const STATUSES: EvaluationStatus[] = [
  'DRAFT',
  'INCOMPLETE',
  'COMPLETE',
  'SUBMITTED',
  'APPROVED',
  'LOCKED',
  'ARCHIVED',
];

describe('EvaluationStatusBadge', () => {
  it.each(STATUSES)('renders icon + label for %s', (status) => {
    const { container } = render(
      renderWithProviders(<EvaluationStatusBadge status={status} />),
    );
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    const span = container.querySelector('span');
    expect(span?.getAttribute('aria-label')).toBeTruthy();
  });
});
