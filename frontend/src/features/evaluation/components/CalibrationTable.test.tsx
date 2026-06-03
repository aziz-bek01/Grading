import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { CalibrationTable } from './CalibrationTable';
import type { Factor } from '@/features/methodology/types';
import type { CalibrationEvent } from '../types';

const factors: Factor[] = [
  {
    id: 'f-1',
    methodology_version_id: 'v-1',
    code: 'F1',
    name_i18n: { 'ru-RU': 'Знания', 'en-US': 'Knowledge' },
    weight: 10,
    max_points: 100,
    sort_order: 0,
    required: true,
    levels: [],
  },
];

describe('CalibrationTable', () => {
  it('renders empty state when no events', () => {
    render(renderWithProviders(<CalibrationTable events={[]} factors={factors} />));
    expect(screen.getByTestId('calibration-table-empty')).toBeInTheDocument();
  });

  it('color-codes delta: positive=success, negative=danger', () => {
    const events: CalibrationEvent[] = [
      {
        id: 'c-1',
        evaluation_id: 'e-1',
        factor_id: 'f-1',
        original_score: 7.5,
        adjusted_score: 9,
        delta: 1.5,
        reason: 'Committee unanimously agreed',
        decided_at: '2026-05-20T12:00:00Z',
        decided_by: 'Dilshod',
      },
      {
        id: 'c-2',
        evaluation_id: 'e-1',
        factor_id: 'f-1',
        original_score: 9,
        adjusted_score: 8,
        delta: -1,
        reason: 'Re-evaluation after audit',
        decided_at: '2026-05-21T12:00:00Z',
        decided_by: 'Dilshod',
      },
    ];
    render(
      renderWithProviders(<CalibrationTable events={events} factors={factors} />),
    );
    const pos = screen.getByTestId('calibration-delta-c-1');
    expect(pos.getAttribute('data-tone')).toBe('pos');
    expect(pos.textContent).toContain('+1.50');
    const neg = screen.getByTestId('calibration-delta-c-2');
    expect(neg.getAttribute('data-tone')).toBe('neg');
    expect(neg.textContent).toContain('-1.00');
  });
});
