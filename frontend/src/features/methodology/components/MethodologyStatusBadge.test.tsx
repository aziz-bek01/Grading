import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { MethodologyStatusBadge } from './MethodologyStatusBadge';

describe('MethodologyStatusBadge', () => {
  it.each(['DRAFT', 'APPROVED', 'LOCKED', 'ARCHIVED'] as const)(
    'renders icon + label for %s',
    (status) => {
      const { container } = render(renderWithProviders(<MethodologyStatusBadge status={status} />));
      // Each tone has its own icon (svg) and a label span.
      const svg = container.querySelector('svg');
      expect(svg).not.toBeNull();
      const span = container.querySelector('span');
      expect(span?.getAttribute('aria-label')).toBeTruthy();
    },
  );
});
