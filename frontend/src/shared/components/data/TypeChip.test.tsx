import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { TypeChip } from './TypeChip';

describe('TypeChip', () => {
  it('renders the translated label for the given value + labelPrefix', () => {
    const { container } = render(
      renderWithProviders(<TypeChip value="POSITION_CATALOG" labelPrefix="export.type" />),
    );
    // export.type.POSITION_CATALOG === 'Каталог должностей' in ru-RU.
    expect(container.textContent).toBe('Каталог должностей');
  });

  it('omits data-testid entirely when not provided (matches ExportTypeBadge, which sets none)', () => {
    const { container } = render(
      renderWithProviders(<TypeChip value="POSITION_CATALOG" labelPrefix="export.type" />),
    );
    expect(container.querySelector('[data-testid]')).toBeNull();
  });

  it('sets data-testid when provided (matches ReportTypeBadge)', () => {
    const { container } = render(
      renderWithProviders(
        <TypeChip
          value="GRADE_DISTRIBUTION"
          labelPrefix="report.type"
          data-testid="report-type-badge"
        />,
      ),
    );
    expect(container.querySelector('[data-testid="report-type-badge"]')).not.toBeNull();
  });

  it('merges an extra className onto the chip', () => {
    const { container } = render(
      renderWithProviders(
        <TypeChip value="POSITION_CATALOG" labelPrefix="export.type" className="extra-class" />,
      ),
    );
    expect(container.querySelector('span')?.className).toContain('extra-class');
  });
});
