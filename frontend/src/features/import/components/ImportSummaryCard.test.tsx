import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ImportSummaryCard } from './ImportSummaryCard';
import type { ImportBatch } from '../types';

const BASE: ImportBatch = {
  id: 'imp-1',
  projectId: 'proj-1',
  templateCode: 'ORG_STRUCTURE_V1',
  status: 'READY_FOR_REVIEW',
  originalFilename: 'x.xlsx',
  fileSize: 100,
  totalRowCount: 373,
  errorRowCount: 4,
  warningRowCount: 0,
  committedRowCount: 0,
  containsSalaryData: false,
  uploadedAt: '2026-08-10T09:00:00Z',
};

describe('ImportSummaryCard — post-commit status copy (prod bug fix)', () => {
  it('shows "N committed, M failed" for PARTIALLY_COMMITTED — NOT the misleading "commit blocked" copy', () => {
    render(
      renderWithProviders(
        <ImportSummaryCard
          batch={{ ...BASE, status: 'PARTIALLY_COMMITTED', committedRowCount: 369, errorRowCount: 4 }}
        />,
      ),
    );
    const line = screen.getByTestId('import-summary-status-line');
    expect(line.textContent).toContain('369');
    expect(line.textContent).toContain('4');
    // The old, actively-wrong copy must be gone for this status.
    expect(screen.queryByText(/fix errors|блокирована|xatolarni|тузатинг/i)).not.toBeInTheDocument();
  });

  it('shows a committed-count message for COMMITTED — not "commit blocked"', () => {
    render(
      renderWithProviders(
        <ImportSummaryCard batch={{ ...BASE, status: 'COMMITTED', committedRowCount: 10, errorRowCount: 0 }} />,
      ),
    );
    const line = screen.getByTestId('import-summary-status-line');
    expect(line.textContent).toContain('10');
    expect(screen.queryByText(/fix errors|блокирована/i)).not.toBeInTheDocument();
  });

  it('still shows "ready to commit" for READY_FOR_REVIEW/READY_TO_COMMIT (unchanged)', () => {
    render(renderWithProviders(<ImportSummaryCard batch={{ ...BASE, status: 'READY_TO_COMMIT' }} />));
    expect(screen.getByTestId('import-summary-status-line')).toBeInTheDocument();
  });

  it('still shows "commit blocked" for VALIDATION_FAILED (genuine pre-commit blocker)', () => {
    render(renderWithProviders(<ImportSummaryCard batch={{ ...BASE, status: 'VALIDATION_FAILED' }} />));
    expect(screen.getByTestId('import-summary-status-line')).toBeInTheDocument();
  });

  it('shows no status line for a terminal non-commit outcome (CANCELLED)', () => {
    render(renderWithProviders(<ImportSummaryCard batch={{ ...BASE, status: 'CANCELLED' }} />));
    expect(screen.queryByTestId('import-summary-status-line')).not.toBeInTheDocument();
  });
});
