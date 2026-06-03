import { describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ExportRequestDialog } from './ExportRequestDialog';

const VALID_PROJECT_UUID = '11111111-1111-1111-1111-111111111111';

describe('ExportRequestDialog', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      renderWithProviders(
        <ExportRequestDialog
          open={false}
          projectId={VALID_PROJECT_UUID}
          onClose={() => {}}
        />,
      ),
    );
    expect(container.querySelector('[data-testid="export-request-dialog"]')).toBeNull();
  });

  it('shows the salary warning banner only for salary-bearing types', () => {
    render(
      renderWithProviders(
        <ExportRequestDialog
          open
          projectId={VALID_PROJECT_UUID}
          onClose={() => {}}
        />,
      ),
    );
    // Default type is POSITION_CATALOG (non-salary).
    expect(screen.queryByTestId('export-salary-warning')).toBeNull();
    fireEvent.change(screen.getByTestId('export-request-type'), {
      target: { value: 'SALARY_RANGES' },
    });
    expect(screen.getByTestId('export-salary-warning')).toBeInTheDocument();
  });
});
