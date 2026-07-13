import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { FormatChip } from './FormatChip';

const TONE = {
  XLSX: 'bg-success-50 text-success-700 border-success-500/30',
  PDF: 'bg-danger-50 text-danger-700 border-danger-500/30',
};

describe('FormatChip', () => {
  it('renders the raw format code as its text content', () => {
    const { container } = render(
      renderWithProviders(<FormatChip value="XLSX" labelPrefix="export.format" toneMap={TONE} />),
    );
    expect(container.querySelector('span')?.textContent?.trim()).toBe('XLSX');
  });

  it('applies the tone-map classes for the given value (colors are per-feature, passed as a prop)', () => {
    const { container: exportChip } = render(
      renderWithProviders(<FormatChip value="PDF" labelPrefix="export.format" toneMap={TONE} />),
    );
    expect(exportChip.querySelector('span')?.className).toContain('bg-danger-50');

    // Same `value` ("PDF"), a DIFFERENT tone map (report colors PDF red too,
    // but this proves the color truly comes from the prop, not a hardcoded map).
    const REPORT_TONE = { PDF: 'bg-info-50 text-info-700 border-info-500/30' };
    const { container: reportChip } = render(
      renderWithProviders(<FormatChip value="PDF" labelPrefix="report.format" toneMap={REPORT_TONE} />),
    );
    expect(reportChip.querySelector('span')?.className).toContain('bg-info-50');
  });

  it('sets the title tooltip from the translated label', () => {
    const { container } = render(
      renderWithProviders(<FormatChip value="XLSX" labelPrefix="export.format" toneMap={TONE} />),
    );
    // export.format.XLSX === 'Excel (.xlsx)' in ru-RU.
    expect(container.querySelector('span')?.getAttribute('title')).toBe('Excel (.xlsx)');
  });

  it('sets data-testid when provided (matches ReportFormatBadge)', () => {
    const { container } = render(
      renderWithProviders(
        <FormatChip
          value="PDF"
          labelPrefix="report.format"
          toneMap={TONE}
          data-testid="report-format-badge"
        />,
      ),
    );
    expect(container.querySelector('[data-testid="report-format-badge"]')).not.toBeNull();
  });
});
