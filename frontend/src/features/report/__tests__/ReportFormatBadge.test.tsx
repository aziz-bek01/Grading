import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ReportFormatBadge } from '../components/ReportFormatBadge';
import type { ReportFormat } from '../types';

const ALL: ReportFormat[] = ['PDF', 'DOCX', 'XLSX'];

describe('ReportFormatBadge', () => {
  it.each(ALL)('renders %s extension', (fmt) => {
    const { container } = render(renderWithProviders(<ReportFormatBadge format={fmt} />));
    const chip = container.querySelector('[data-testid="report-format-badge"]');
    expect(chip).not.toBeNull();
    expect(chip!.textContent?.trim()).toBe(fmt);
  });
});
