import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { BulkScoreDialog } from './BulkScoreDialog';
import type { Factor } from '@/features/methodology/types';
import type { BulkOperationResult } from '../../types';

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
      // Locale-independent token so the assertion is decoupled from i18n config.
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

const okResult: BulkOperationResult = { updated: 3, failed: [] };

function renderDialog(
  overrides: Partial<{
    canSeePoints: boolean;
    onConfirm: (lvlId: string, reason: string) => Promise<BulkOperationResult>;
    onClose: () => void;
  }> = {},
) {
  const onConfirm =
    overrides.onConfirm ?? vi.fn(async () => okResult);
  const onClose = overrides.onClose ?? vi.fn();
  const utils = render(
    renderWithProviders(
      <BulkScoreDialog
        open
        factor={factor}
        selectedCount={3}
        canSeePoints={overrides.canSeePoints ?? false}
        onConfirm={onConfirm}
        onClose={onClose}
      />,
    ),
  );
  return { ...utils, onConfirm, onClose };
}

describe('BulkScoreDialog — level-by-description redesign', () => {
  it('AC-6: selects by level (code + description) via the shared LevelDropSelect', () => {
    renderDialog();
    fireEvent.click(screen.getByTestId('level-drop-trigger-bulk'));
    const optA = screen.getByTestId('level-drop-option-A-bulk');
    const optB = screen.getByTestId('level-drop-option-B-bulk');
    expect(optA).toHaveTextContent('A');
    expect(optA).toHaveTextContent('DESC_LEVEL_A');
    expect(optB).toHaveTextContent('B');
    expect(optB).toHaveTextContent('DESC_LEVEL_B');
  });

  it('AC-2: plain evaluator sees NO points in the bulk dialog', () => {
    renderDialog({ canSeePoints: false });
    fireEvent.click(screen.getByTestId('level-drop-trigger-bulk'));
    expect(
      screen.queryByTestId('level-drop-option-points-A-bulk'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('level-drop-option-points-B-bulk'),
    ).not.toBeInTheDocument();
    const list = screen.getByTestId('level-drop-list-bulk');
    expect(list.textContent).not.toContain('25');
    expect(list.textContent).not.toContain('75');
  });

  it('AC-2b: admin/HR-director (canSeePoints) sees points in the bulk dialog', () => {
    renderDialog({ canSeePoints: true });
    fireEvent.click(screen.getByTestId('level-drop-trigger-bulk'));
    expect(
      screen.getByTestId('level-drop-option-points-A-bulk'),
    ).toHaveTextContent('25');
    expect(
      screen.getByTestId('level-drop-option-points-B-bulk'),
    ).toHaveTextContent('75');
  });

  it('confirms with the picked level id (existing payload) once a ≥50-char reason is given', async () => {
    const onConfirm = vi.fn(async () => okResult);
    renderDialog({ onConfirm });

    // Submit is disabled until both a level and a valid reason exist.
    const confirm = screen.getByTestId('bulk-score-confirm') as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);

    // Pick a level (closes the drop).
    fireEvent.click(screen.getByTestId('level-drop-trigger-bulk'));
    fireEvent.click(screen.getByTestId('level-drop-option-B-bulk'));
    await waitFor(() =>
      expect(screen.queryByTestId('level-drop-list-bulk')).not.toBeInTheDocument(),
    );

    // Still disabled — reason too short.
    expect((screen.getByTestId('bulk-score-confirm') as HTMLButtonElement).disabled).toBe(
      true,
    );

    const reason = 'Calibration committee aligned every selected position to level B.';
    fireEvent.change(screen.getByTestId('bulk-score-reason'), {
      target: { value: reason },
    });

    fireEvent.click(screen.getByTestId('bulk-score-confirm'));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('lvl-2', reason));
  });
});
