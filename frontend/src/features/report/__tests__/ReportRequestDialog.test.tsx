import { describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ReportRequestDialog } from '../components/ReportRequestDialog';

const VALID_PROJECT_UUID = '11111111-1111-1111-1111-111111111111';

function openDialog() {
  render(
    renderWithProviders(
      <ReportRequestDialog open projectId={VALID_PROJECT_UUID} onClose={() => {}} />,
    ),
  );
}

describe('ReportRequestDialog', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      renderWithProviders(
        <ReportRequestDialog open={false} projectId={VALID_PROJECT_UUID} onClose={() => {}} />,
      ),
    );
    expect(container.querySelector('[data-testid="report-request-dialog"]')).toBeNull();
  });

  it('enables all 3 formats for GRADE_DISTRIBUTION', () => {
    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'GRADE_DISTRIBUTION' },
    });
    const select = screen.getByTestId('report-request-format') as HTMLSelectElement;
    const enabled = Array.from(select.options)
      .filter((o) => !o.disabled)
      .map((o) => o.value);
    expect(enabled.sort()).toEqual(['DOCX', 'PDF', 'XLSX']);
  });

  it('limits METHODOLOGY_SPEC to PDF + DOCX (XLSX disabled)', () => {
    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'METHODOLOGY_SPEC' },
    });
    const select = screen.getByTestId('report-request-format') as HTMLSelectElement;
    const opts = Object.fromEntries(
      Array.from(select.options).map((o) => [o.value, !o.disabled]),
    );
    expect(opts.PDF).toBe(true);
    expect(opts.DOCX).toBe(true);
    expect(opts.XLSX).toBe(false);
  });

  it('limits AUDIT_SUMMARY to PDF + XLSX (DOCX disabled)', () => {
    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'AUDIT_SUMMARY' },
    });
    const select = screen.getByTestId('report-request-format') as HTMLSelectElement;
    const opts = Object.fromEntries(
      Array.from(select.options).map((o) => [o.value, !o.disabled]),
    );
    expect(opts.PDF).toBe(true);
    expect(opts.XLSX).toBe(true);
    expect(opts.DOCX).toBe(false);
  });

  it('limits EXECUTIVE_SUMMARY to PDF only (DOCX + XLSX disabled)', () => {
    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'EXECUTIVE_SUMMARY' },
    });
    const select = screen.getByTestId('report-request-format') as HTMLSelectElement;
    const opts = Object.fromEntries(
      Array.from(select.options).map((o) => [o.value, !o.disabled]),
    );
    expect(opts.PDF).toBe(true);
    expect(opts.DOCX).toBe(false);
    expect(opts.XLSX).toBe(false);
  });
});
