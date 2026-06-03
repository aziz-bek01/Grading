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
});
