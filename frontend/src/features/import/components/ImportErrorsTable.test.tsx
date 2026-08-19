import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ImportErrorsTable } from './ImportErrorsTable';
import type { ImportError } from '../types';

const sample: ImportError[] = [
  { id: '1', errorLevel: 'BLOCKER', errorCode: 'CRITICAL', message: 'Critical issue', rowNumber: 1 },
  { id: '2', errorLevel: 'ERROR', errorCode: 'ROW_INVALID', message: 'Bad row', rowNumber: 2 },
  { id: '3', errorLevel: 'WARNING', errorCode: 'WARN', message: 'Warn me', rowNumber: 3 },
  { id: '4', errorLevel: 'INFO', errorCode: 'NOTE', message: 'Just a note', rowNumber: 4 },
];

describe('ImportErrorsTable', () => {
  it('renders all rows when no filter applied', () => {
    render(renderWithProviders(<ImportErrorsTable errors={sample} />));
    expect(screen.getByText('Critical issue')).toBeInTheDocument();
    expect(screen.getByText('Bad row')).toBeInTheDocument();
    expect(screen.getByText('Warn me')).toBeInTheDocument();
    expect(screen.getByText('Just a note')).toBeInTheDocument();
  });

  it('shows empty state with no errors', () => {
    render(renderWithProviders(<ImportErrorsTable errors={[]} />));
    // Empty state text comes from i18n; assert by absence of table.
    expect(screen.queryByText('Critical issue')).not.toBeInTheDocument();
  });

  it('exposes a filter row with 5 buttons (All + 4 levels)', () => {
    const { container } = render(
      renderWithProviders(<ImportErrorsTable errors={sample} />),
    );
    const buttons = container.querySelectorAll('button');
    // 5 filter buttons (All + 4 levels).
    expect(buttons.length).toBe(5);
  });

  // ---------------------------------------------------------------------
  // Per-level counts — a batch that lands on (or is left on) a level with
  // zero rows must not read as "no problems": every filter button always
  // shows the TRUE count for that level, computed from the full set, not
  // the currently-filtered view (prod bug: 4 real ERROR-level row failures
  // were invisible while the "Info" tab happened to be selected).
  // ---------------------------------------------------------------------

  it('shows a true per-level count on every filter button, independent of the active filter', () => {
    render(renderWithProviders(<ImportErrorsTable errors={sample} />));
    expect(screen.getByTestId('import-errors-filter-all').textContent).toContain('4');
    expect(screen.getByTestId('import-errors-filter-BLOCKER').textContent).toContain('1');
    expect(screen.getByTestId('import-errors-filter-ERROR').textContent).toContain('1');
    expect(screen.getByTestId('import-errors-filter-WARNING').textContent).toContain('1');
    expect(screen.getByTestId('import-errors-filter-INFO').textContent).toContain('1');
  });

  it('keeps the ERROR count visible even while the INFO tab is selected (the exact prod symptom)', () => {
    const onlyInfoSelected: ImportError[] = [
      { id: '1', errorLevel: 'ERROR', errorCode: 'ROW_INVALID', message: 'Bad row', rowNumber: 2 },
      { id: '2', errorLevel: 'ERROR', errorCode: 'ROW_INVALID', message: 'Bad row 2', rowNumber: 5 },
      { id: '3', errorLevel: 'ERROR', errorCode: 'ROW_INVALID', message: 'Bad row 3', rowNumber: 8 },
      { id: '4', errorLevel: 'ERROR', errorCode: 'ROW_INVALID', message: 'Bad row 4', rowNumber: 9 },
    ];
    render(
      renderWithProviders(<ImportErrorsTable errors={onlyInfoSelected} level="INFO" />),
    );
    // The "no problems at this level" empty state is legitimately shown for
    // INFO (there are none) — but the ERROR tab must clearly say "4" right
    // next to it so the failures are never hidden from view.
    expect(screen.getByText(/no issues|проблем на этом уровне нет|муаммолар йўқ|muammolar yoʻq/i)).toBeInTheDocument();
    expect(screen.getByTestId('import-errors-filter-ERROR').textContent).toContain('4');
  });
});
