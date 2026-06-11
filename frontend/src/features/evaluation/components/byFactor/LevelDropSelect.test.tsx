import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { LevelDropSelect } from './LevelDropSelect';
import type { Factor } from '@/features/methodology/types';

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

function renderDrop(
  overrides: Partial<{
    selectedLevelId: string | null;
    onSelect: (id: string) => void;
    contextTitle: string;
    disabled: boolean;
    canSeePoints: boolean;
  }> = {},
) {
  const onSelect = overrides.onSelect ?? vi.fn();
  const utils = render(
    renderWithProviders(
      <LevelDropSelect
        factor={factor}
        selectedLevelId={overrides.selectedLevelId ?? null}
        onSelect={onSelect}
        contextTitle={overrides.contextTitle}
        disabled={overrides.disabled}
        canSeePoints={overrides.canSeePoints ?? false}
        testIdSuffix="POS-1"
      />,
    ),
  );
  return { ...utils, onSelect };
}

describe('LevelDropSelect — centered modal (FE-10)', () => {
  it('opens a CENTERED MODAL (role=dialog) — not an in-cell absolute dropdown', () => {
    renderDrop();
    // Collapsed: no modal yet.
    expect(screen.queryByTestId('level-drop-modal-POS-1')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    const modal = screen.getByTestId('level-drop-modal-POS-1');
    expect(modal).toBeInTheDocument();
    // Centered overlay primitive: the SAME fixed inset-0 z-50 dialog chrome.
    expect(modal.className).toContain('fixed');
    expect(modal.className).toContain('inset-0');
    expect(modal.className).toContain('z-50');
    expect(modal.getAttribute('role')).toBe('dialog');
  });

  it('lists ALL levels (the SAME single list) with full descriptions', () => {
    renderDrop();
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    const list = screen.getByTestId('level-drop-list-POS-1');
    expect(list).toBeInTheDocument();
    expect(screen.getByTestId('level-drop-option-A-POS-1')).toHaveTextContent(
      'DESC_LEVEL_A',
    );
    expect(screen.getByTestId('level-drop-option-B-POS-1')).toHaveTextContent(
      'DESC_LEVEL_B',
    );
  });

  it('titles the modal with the position context (contextTitle)', () => {
    renderDrop({ contextTitle: 'Финансовый аналитик' });
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    expect(
      screen.getByTestId('level-drop-modal-title-POS-1'),
    ).toHaveTextContent('Финансовый аналитик');
  });

  it('picking a level closes the modal and fires onSelect with the unchanged payload', async () => {
    const onSelect = vi.fn();
    renderDrop({ onSelect });
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    fireEvent.click(screen.getByTestId('level-drop-option-B-POS-1'));
    expect(onSelect).toHaveBeenCalledWith('lvl-2');
    await waitFor(() =>
      expect(screen.queryByTestId('level-drop-modal-POS-1')).not.toBeInTheDocument(),
    );
  });

  it('does not open the modal when disabled', () => {
    renderDrop({ disabled: true });
    fireEvent.click(screen.getByTestId('level-drop-trigger-POS-1'));
    expect(screen.queryByTestId('level-drop-modal-POS-1')).not.toBeInTheDocument();
  });
});
