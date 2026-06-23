import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ReportRequestDialog } from '../components/ReportRequestDialog';

const VALID_PROJECT_UUID = '11111111-1111-4111-8111-111111111111';

const METHODOLOGY_ID = '22222222-2222-4222-8222-222222222222';
const VERSION_ID = '33333333-3333-4333-8333-333333333333';
const EVALUATOR_ID = '44444444-4444-4444-8444-444444444444';

/**
 * Stubs the GET calls the EVALUATION_SUMMARY filter panel issues
 * (`useMethodologies` -> `/methodologies`, `useMethodologyVersions` ->
 * `/methodologies/{id}/versions`, `EvaluatorPicker` -> `/users`) so the
 * pickers render deterministic, addressable options without depending on
 * the MSW fixture seed for an arbitrary project id.
 */
function stubFilterPanelData() {
  return vi.spyOn(httpClient, 'get').mockImplementation((url: string) => {
    if (url === '/methodologies') {
      return Promise.resolve({
        data: {
          items: [
            {
              id: METHODOLOGY_ID,
              project_id: VALID_PROJECT_UUID,
              code: 'CLASSIC_8',
              name_i18n: { 'ru-RU': 'Классическая 8-факторная' },
              methodology_type: 'CLASSIC_8_FACTOR',
              status: 'ACTIVE',
              active_version_id: VERSION_ID,
              active_version_number: 1,
            },
          ],
        },
      } as unknown as Awaited<ReturnType<typeof httpClient.get>>);
    }
    if (url === `/methodologies/${METHODOLOGY_ID}/versions`) {
      return Promise.resolve({
        data: [
          {
            id: VERSION_ID,
            version_number: 1,
            status: 'APPROVED',
            scoring_mode: 'WEIGHTED_POINTS',
            updated_at: '2026-01-01T00:00:00Z',
          },
        ],
      } as unknown as Awaited<ReturnType<typeof httpClient.get>>);
    }
    if (url === '/users') {
      return Promise.resolve({
        data: {
          items: [
            { id: EVALUATOR_ID, full_name: 'Aliyev A.', status: 'ACTIVE' },
          ],
        },
      } as unknown as Awaited<ReturnType<typeof httpClient.get>>);
    }
    return Promise.resolve({ data: {} } as unknown as Awaited<ReturnType<typeof httpClient.get>>);
  });
}

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

describe('ReportRequestDialog — EVALUATION_SUMMARY filter panel', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('does not render the filter panel for non-EVALUATION_SUMMARY types', () => {
    stubFilterPanelData();
    openDialog();
    expect(screen.queryByTestId('report-request-evaluation-filters')).toBeNull();
  });

  it('renders methodology-version, date-range, and evaluator controls for EVALUATION_SUMMARY', async () => {
    stubFilterPanelData();
    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'EVALUATION_SUMMARY' },
    });

    expect(screen.getByTestId('report-request-evaluation-filters')).toBeInTheDocument();

    // Methodology version multi-select (checkbox keyed by version id).
    await waitFor(() =>
      expect(screen.getByTestId(`report-filter-methodology-version-${VERSION_ID}`)).toBeInTheDocument(),
    );

    // Date range — reused DateRangeFields, NOT a second hand-rolled picker.
    expect(screen.getByTestId('report-filter-date-from')).toBeInTheDocument();
    expect(screen.getByTestId('report-filter-date-to')).toBeInTheDocument();

    // Evaluator multi-select (EvaluatorPicker mode="multi").
    await waitFor(() =>
      expect(screen.getByTestId(`report-filter-evaluator-${EVALUATOR_ID}`)).toBeInTheDocument(),
    );
  });

  it('serializes the selected filters into a snake_case filter_params JSON string on submit', async () => {
    stubFilterPanelData();
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({
      data: {
        id: 'report-1',
        project_id: VALID_PROJECT_UUID,
        report_type: 'EVALUATION_SUMMARY',
        format: 'PDF',
        status: 'REQUESTED',
        requested_at: '2026-06-23T00:00:00Z',
      },
    } as unknown as Awaited<ReturnType<typeof httpClient.post>>);

    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'EVALUATION_SUMMARY' },
    });

    await waitFor(() =>
      expect(screen.getByTestId(`report-filter-methodology-version-${VERSION_ID}`)).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId(`report-filter-methodology-version-${VERSION_ID}`));

    fireEvent.change(screen.getByTestId('report-filter-date-from'), {
      target: { value: '2026-04-01' },
    });
    fireEvent.change(screen.getByTestId('report-filter-date-to'), {
      target: { value: '2026-06-30' },
    });

    await waitFor(() =>
      expect(screen.getByTestId(`report-filter-evaluator-${EVALUATOR_ID}`)).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId(`report-filter-evaluator-${EVALUATOR_ID}`));

    fireEvent.click(screen.getByTestId('report-request-submit'));

    await waitFor(() => expect(postSpy).toHaveBeenCalled());

    const [, body] = postSpy.mock.calls[0] as [string, Record<string, unknown>];
    expect(body.report_type).toBe('EVALUATION_SUMMARY');
    expect(typeof body.filter_params).toBe('string');

    const parsed = JSON.parse(body.filter_params as string) as Record<string, unknown>;
    // Regression class behind commit a45db24: the wire object's OWN keys
    // must be snake_case, never camelCase.
    expect(parsed).toEqual({
      methodology_version_ids: [VERSION_ID],
      date_from: '2026-04-01',
      date_to: '2026-06-30',
      evaluator_user_ids: [EVALUATOR_ID],
    });
    expect(parsed.methodologyVersionIds).toBeUndefined();
    expect(parsed.dateFrom).toBeUndefined();
    expect(parsed.dateTo).toBeUndefined();
    expect(parsed.evaluatorUserIds).toBeUndefined();
  });

  it('omits empty filter dimensions instead of sending empty arrays/strings (empty filter = no narrowing)', async () => {
    stubFilterPanelData();
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({
      data: {
        id: 'report-2',
        project_id: VALID_PROJECT_UUID,
        report_type: 'EVALUATION_SUMMARY',
        format: 'PDF',
        status: 'REQUESTED',
        requested_at: '2026-06-23T00:00:00Z',
      },
    } as unknown as Awaited<ReturnType<typeof httpClient.post>>);

    openDialog();
    fireEvent.change(screen.getByTestId('report-request-type'), {
      target: { value: 'EVALUATION_SUMMARY' },
    });

    fireEvent.click(screen.getByTestId('report-request-submit'));

    await waitFor(() => expect(postSpy).toHaveBeenCalled());
    const [, body] = postSpy.mock.calls[0] as [string, Record<string, unknown>];
    // No filters selected -> filter_params is null, not '{}' or an empty-array JSON blob.
    expect(body.filter_params).toBeNull();
  });
});
