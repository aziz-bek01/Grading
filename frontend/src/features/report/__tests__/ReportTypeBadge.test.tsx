import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ReportTypeBadge } from '../components/ReportTypeBadge';
import type { ReportType } from '../types';

const ALL: ReportType[] = [
  'GRADE_DISTRIBUTION',
  'POSITION_CATALOG',
  'EVALUATION_SUMMARY',
  'METHODOLOGY_SPEC',
  'AUDIT_SUMMARY',
  'EXECUTIVE_SUMMARY',
];

describe('ReportTypeBadge', () => {
  it.each(ALL)('renders translated label for %s', (type) => {
    const { container } = render(renderWithProviders(<ReportTypeBadge type={type} />));
    const chip = container.querySelector('[data-testid="report-type-badge"]');
    expect(chip).not.toBeNull();
    const text = chip!.textContent ?? '';
    // Label must resolve via i18n (not the raw key path).
    expect(text.length).toBeGreaterThan(0);
    expect(text).not.toContain('report.type.');
  });
});
