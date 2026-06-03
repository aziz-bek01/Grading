import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { PositionScoreRow } from './PositionScoreRow';
import type { Factor } from '@/features/methodology/types';
import type { EvaluationByFactorRow } from '../../types';

const factor: Factor = {
  id: 'f-test',
  methodology_version_id: 'mv-test',
  code: 'K1',
  name_i18n: { 'ru-RU': 'Знания', 'en-US': 'Knowledge' },
  weight: 15,
  max_points: 100,
  sort_order: 0,
  required: true,
  levels: [
    { id: 'lvl-1', factor_id: 'f-test', code: 'A', level_order: 0, points: 25, scale_value: 0.25, label_i18n: { 'ru-RU': 'A', 'en-US': 'A' } },
    { id: 'lvl-2', factor_id: 'f-test', code: 'B', level_order: 1, points: 75, scale_value: 0.75, label_i18n: { 'ru-RU': 'B', 'en-US': 'B' } },
  ],
};

function makeRow(overrides: Partial<EvaluationByFactorRow> = {}): EvaluationByFactorRow {
  return {
    evaluation_id: 'eval-1',
    position_id: 'pos-1',
    position_code: 'POS-1',
    position_title: 'Финансовый аналитик',
    department_name: 'Финансы',
    unit_name: null,
    status: 'DRAFT',
    filled_factors_count: 0,
    total_factors_count: 8,
    current_score_factor_level_id: null,
    current_score_raw_value: null,
    current_comment: null,
    ...overrides,
  };
}

function renderRow(
  rowOverrides: Partial<EvaluationByFactorRow> = {},
  handlers: Partial<{
    onScoreChange: (lvlId: string) => Promise<void>;
    onCommentChange: (c: string) => Promise<void>;
    onRowSelect: () => void;
    onBulkToggle: (on: boolean) => void;
    canEdit: boolean;
  }> = {},
) {
  const onScoreChange = handlers.onScoreChange ?? vi.fn(async () => {});
  const onCommentChange = handlers.onCommentChange ?? vi.fn(async () => {});
  const onRowSelect = handlers.onRowSelect ?? vi.fn();
  const onBulkToggle = handlers.onBulkToggle ?? vi.fn();
  const canEdit = handlers.canEdit ?? true;
  const row = makeRow(rowOverrides);
  const utils = render(
    renderWithProviders(
      <table>
        <tbody>
          <PositionScoreRow
            row={row}
            factor={factor}
            selected={false}
            bulkSelected={false}
            canEdit={canEdit}
            onScoreChange={onScoreChange}
            onCommentChange={onCommentChange}
            onRowSelect={onRowSelect}
            onBulkToggle={onBulkToggle}
          />
        </tbody>
      </table>,
    ),
  );
  return { ...utils, row, onScoreChange, onCommentChange, onRowSelect, onBulkToggle };
}

describe('PositionScoreRow', () => {
  it('emits onScoreChange optimistically when the select changes', async () => {
    const onScoreChange = vi.fn(async () => {});
    renderRow({}, { onScoreChange });
    const select = screen.getByTestId('row-score-POS-1') as HTMLSelectElement;
    expect(select.value).toBe('');
    fireEvent.change(select, { target: { value: 'lvl-2' } });
    // Optimistic: the select value flips immediately.
    expect(select.value).toBe('lvl-2');
    await waitFor(() => expect(onScoreChange).toHaveBeenCalledWith('lvl-2'));
  });

  it('disables score + comment and renders lock icon for SUBMITTED status', () => {
    renderRow({ status: 'SUBMITTED' });
    const select = screen.getByTestId('row-score-POS-1') as HTMLSelectElement;
    expect(select.disabled).toBe(true);
    const comment = screen.getByTestId('row-comment-POS-1') as HTMLTextAreaElement;
    expect(comment.disabled).toBe(true);
    expect(screen.getByTestId('row-lock-POS-1')).toBeInTheDocument();
    expect(screen.getByTestId(`position-row-POS-1`).getAttribute('data-locked')).toBe('true');
  });

  it('debounces the autosave comment by 300ms', async () => {
    vi.useFakeTimers();
    try {
      const onCommentChange = vi.fn(async () => {});
      renderRow({ current_score_factor_level_id: 'lvl-1' }, { onCommentChange });
      const comment = screen.getByTestId('row-comment-POS-1') as HTMLTextAreaElement;
      fireEvent.change(comment, { target: { value: 'New explanation' } });
      // Before the debounce elapses, no save call yet.
      expect(onCommentChange).not.toHaveBeenCalled();
      vi.advanceTimersByTime(310);
      expect(onCommentChange).toHaveBeenCalledWith('New explanation');
    } finally {
      vi.useRealTimers();
    }
  });
});
