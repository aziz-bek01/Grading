import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { PositionScoreRow } from './PositionScoreRow';
import type { Factor } from '@/features/methodology/types';
import type { EvaluationByFactorRow } from '../../types';

/**
 * Test factor with real level DESCRIPTIONS so we can assert the K-sheet redesign:
 * evaluators pick a level by its description text (not a numeric score), code
 * and description are both rendered, and point values are surfaced ONLY when
 * `canSeePoints` is true (project-admin / HR-director exception).
 */
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
    {
      id: 'lvl-1',
      factor_id: 'f-test',
      code: 'A',
      level_order: 0,
      points: 25,
      scale_value: 0.25,
      label_i18n: { 'ru-RU': 'A', 'en-US': 'A' },
      // Locale-independent description token so the assertion does not depend
      // on which locale `pickLocalized` resolves to in the test runtime.
      description_i18n: {
        'ru-RU': 'DESC_LEVEL_A',
        'uz-Cyrl-UZ': 'DESC_LEVEL_A',
        'uz-Latn-UZ': 'DESC_LEVEL_A',
        'en-US': 'DESC_LEVEL_A',
      },
    },
    {
      id: 'lvl-2',
      factor_id: 'f-test',
      code: 'B',
      level_order: 1,
      points: 75,
      scale_value: 0.75,
      label_i18n: { 'ru-RU': 'B', 'en-US': 'B' },
      description_i18n: {
        'ru-RU': 'DESC_LEVEL_B',
        'uz-Cyrl-UZ': 'DESC_LEVEL_B',
        'uz-Latn-UZ': 'DESC_LEVEL_B',
        'en-US': 'DESC_LEVEL_B',
      },
    },
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
    canSeePoints: boolean;
  }> = {},
) {
  const onScoreChange = handlers.onScoreChange ?? vi.fn(async () => {});
  const onCommentChange = handlers.onCommentChange ?? vi.fn(async () => {});
  const onRowSelect = handlers.onRowSelect ?? vi.fn();
  const onBulkToggle = handlers.onBulkToggle ?? vi.fn();
  const canEdit = handlers.canEdit ?? true;
  const canSeePoints = handlers.canSeePoints ?? false;
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
            canSeePoints={canSeePoints}
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

describe('PositionScoreRow — K-sheet level-drop redesign', () => {
  it('AC-7: unselected row shows a clear "not selected" placeholder (no level chosen)', () => {
    renderRow();
    const trigger = screen.getByTestId('level-drop-trigger-POS-1');
    // Warning "—" badge instead of a level code, and the i18n placeholder.
    expect(trigger).toHaveTextContent('—');
    expect(trigger).not.toHaveTextContent('DESC_LEVEL_A');
    expect(trigger).not.toHaveTextContent('DESC_LEVEL_B');
    // No legacy numeric <select> exists anymore.
    expect(screen.queryByTestId('row-score-POS-1')).not.toBeInTheDocument();
  });

  it('AC-3: a selected level renders code + description collapsed in the cell', () => {
    renderRow({ current_score_factor_level_id: 'lvl-2' });
    const trigger = screen.getByTestId('level-drop-trigger-POS-1');
    expect(trigger).toHaveTextContent('B'); // level code badge
    expect(trigger).toHaveTextContent('DESC_LEVEL_B'); // description
    // The drop is collapsed until clicked.
    expect(screen.queryByTestId('level-drop-list-POS-1')).not.toBeInTheDocument();
  });

  it('AC-4: expanding the drop lists ALL levels with code + description', () => {
    renderRow();
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    const list = screen.getByTestId('level-drop-list-POS-1');
    expect(list).toBeInTheDocument();
    const optA = screen.getByTestId('level-drop-option-A-POS-1');
    const optB = screen.getByTestId('level-drop-option-B-POS-1');
    expect(optA).toHaveTextContent('A');
    expect(optA).toHaveTextContent('DESC_LEVEL_A');
    expect(optB).toHaveTextContent('B');
    expect(optB).toHaveTextContent('DESC_LEVEL_B');
  });

  it('AC-2: plain evaluator sees NO point number — collapsed, open list, anywhere', () => {
    renderRow({ current_score_factor_level_id: 'lvl-1' }, { canSeePoints: false });
    // Collapsed control: no point badge.
    expect(screen.queryByTestId('level-drop-points-POS-1')).not.toBeInTheDocument();
    // Open the list: still no per-option point badges.
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    expect(
      screen.queryByTestId('level-drop-option-points-A-POS-1'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('level-drop-option-points-B-POS-1'),
    ).not.toBeInTheDocument();
    // Raw numbers (25 / 75) must not leak into the rendered cell.
    const drop = screen.getByTestId('level-drop-POS-1');
    expect(drop.textContent).not.toContain('25');
    expect(drop.textContent).not.toContain('75');
  });

  it('AC-2b: admin/HR-director (canSeePoints) sees muted point values next to levels', () => {
    renderRow({ current_score_factor_level_id: 'lvl-1' }, { canSeePoints: true });
    // Collapsed: the selected level's point value is shown.
    expect(screen.getByTestId('level-drop-points-POS-1')).toHaveTextContent('25');
    // Open list: each option carries its point value.
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    expect(
      screen.getByTestId('level-drop-option-points-A-POS-1'),
    ).toHaveTextContent('25');
    expect(
      screen.getByTestId('level-drop-option-points-B-POS-1'),
    ).toHaveTextContent('75');
  });

  it('AC-5: picking a level closes the drop and fires the existing mutation with the level id', async () => {
    const onScoreChange = vi.fn(async () => {});
    renderRow({}, { onScoreChange });
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    fireEvent.click(screen.getByTestId('level-drop-option-B-POS-1'));
    // Same payload shape as the old <select>: the bare factor-level id.
    await waitFor(() => expect(onScoreChange).toHaveBeenCalledWith('lvl-2'));
    expect(onScoreChange).toHaveBeenCalledTimes(1);
    // Drop closes after a pick.
    await waitFor(() =>
      expect(screen.queryByTestId('level-drop-list-POS-1')).not.toBeInTheDocument(),
    );
    // Collapsed control updates optimistically to the picked level.
    expect(screen.getByTestId('level-drop-trigger-POS-1')).toHaveTextContent('B');
  });

  it('disables the level drop + comment and renders a lock icon for SUBMITTED status', () => {
    renderRow({ status: 'SUBMITTED' });
    const trigger = screen.getByTestId('level-drop-trigger-POS-1') as HTMLButtonElement;
    expect(trigger.disabled).toBe(true);
    const comment = screen.getByTestId('row-comment-POS-1') as HTMLTextAreaElement;
    expect(comment.disabled).toBe(true);
    expect(screen.getByTestId('row-lock-POS-1')).toBeInTheDocument();
    expect(
      screen.getByTestId('position-row-POS-1').getAttribute('data-locked'),
    ).toBe('true');
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
