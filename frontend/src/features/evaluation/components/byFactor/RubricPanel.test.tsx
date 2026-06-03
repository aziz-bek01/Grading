import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { RubricPanel } from './RubricPanel';
import type { Factor } from '@/features/methodology/types';

const COLLAPSE_KEY = 'eval.byFactor.rubric.collapsed';

const factor: Factor = {
  id: 'f-test',
  methodology_version_id: 'mv-test',
  code: 'K1',
  name_i18n: {
    'ru-RU': 'Знания',
    'uz-Cyrl-UZ': 'Билимлар',
    'uz-Latn-UZ': 'Bilimlar',
    'en-US': 'Knowledge',
  },
  description_i18n: {
    'ru-RU': 'Глубина профессиональных знаний.',
    'en-US': 'Depth of professional knowledge.',
  },
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
      label_i18n: {
        'ru-RU': 'Начальный',
        'uz-Cyrl-UZ': 'Бошланғич',
        'uz-Latn-UZ': 'Boshlangʻich',
        'en-US': 'Beginner',
      },
    },
    {
      id: 'lvl-2',
      factor_id: 'f-test',
      code: 'B',
      level_order: 1,
      points: 75,
      scale_value: 0.75,
      label_i18n: {
        'ru-RU': 'Продвинутый',
        'uz-Cyrl-UZ': 'Илғор',
        'uz-Latn-UZ': "Ilg'or",
        'en-US': 'Advanced',
      },
    },
  ],
};

describe('RubricPanel', () => {
  beforeEach(() => {
    // Reset persisted collapse choice so each test owns its initial state.
    window.localStorage.removeItem(COLLAPSE_KEY);
  });

  it('renders factor + level cards in the default locale', () => {
    render(
      renderWithProviders(
        <RubricPanel factor={factor} selectedLevelId={null} />,
      ),
    );
    expect(screen.getByTestId('rubric-panel')).toBeInTheDocument();
    // Russian is the seed locale in renderWithProviders → I18nProvider.
    expect(screen.getByText('Знания')).toBeInTheDocument();
    expect(screen.getByText('Начальный')).toBeInTheDocument();
    expect(screen.getByText('Продвинутый')).toBeInTheDocument();
  });

  it('switches level labels when the locale tab is clicked', () => {
    render(
      renderWithProviders(
        <RubricPanel factor={factor} selectedLevelId={null} />,
      ),
    );
    fireEvent.click(screen.getByTestId('rubric-locale-en-US'));
    expect(screen.getByText('Knowledge')).toBeInTheDocument();
    expect(screen.getByText('Beginner')).toBeInTheDocument();
    expect(screen.getByText('Advanced')).toBeInTheDocument();
  });

  it('marks the selected level card with the success outline', () => {
    render(
      renderWithProviders(
        <RubricPanel factor={factor} selectedLevelId="lvl-2" />,
      ),
    );
    const selected = screen.getByTestId('rubric-level-B');
    expect(selected.getAttribute('data-selected')).toBe('true');
    const other = screen.getByTestId('rubric-level-A');
    expect(other.getAttribute('data-selected')).toBeNull();
  });

  it('toggles collapse state and persists choice to localStorage', () => {
    render(
      renderWithProviders(
        <RubricPanel factor={factor} selectedLevelId={null} />,
      ),
    );

    const panel = screen.getByTestId('rubric-panel');
    expect(panel.getAttribute('data-collapsed')).toBe('false');
    // Level cards are visible in expanded mode.
    expect(screen.getByText('Начальный')).toBeInTheDocument();

    // Click the header chevron to collapse.
    fireEvent.click(screen.getByTestId('rubric-toggle'));

    const collapsedPanel = screen.getByTestId('rubric-panel');
    expect(collapsedPanel.getAttribute('data-collapsed')).toBe('true');
    // Level cards are removed when collapsed (only vertical label + chevron remain).
    expect(screen.queryByText('Начальный')).not.toBeInTheDocument();
    // Persistence: localStorage was updated to '1'.
    expect(window.localStorage.getItem(COLLAPSE_KEY)).toBe('1');

    // Click again to expand — verifies round-trip toggle + storage flip.
    fireEvent.click(screen.getByTestId('rubric-toggle'));
    expect(screen.getByTestId('rubric-panel').getAttribute('data-collapsed')).toBe(
      'false',
    );
    expect(window.localStorage.getItem(COLLAPSE_KEY)).toBe('0');
  });

  it('starts collapsed when localStorage flag is preset to "1"', () => {
    window.localStorage.setItem(COLLAPSE_KEY, '1');
    render(
      renderWithProviders(
        <RubricPanel factor={factor} selectedLevelId={null} />,
      ),
    );
    const panel = screen.getByTestId('rubric-panel');
    expect(panel.getAttribute('data-collapsed')).toBe('true');
    // Level cards must not render in the collapsed initial state.
    expect(screen.queryByText('Знания')).not.toBeInTheDocument();
  });

  it('defaults to EXPANDED on first visit (no stored preference)', () => {
    // beforeEach already cleared the key — assert the legend is visible so a
    // first-time evaluator is never trapped in a hidden-legend state.
    render(
      renderWithProviders(
        <RubricPanel factor={factor} selectedLevelId={null} />,
      ),
    );
    const panel = screen.getByTestId('rubric-panel');
    expect(panel.getAttribute('data-collapsed')).toBe('false');
    expect(screen.getByText('Начальный')).toBeInTheDocument();
  });

  describe('embedded mode (drawer host)', () => {
    it('always renders expanded content even when collapse is persisted', () => {
      // A power user collapsed the docked rail — the drawer must IGNORE that
      // and still show the full legend (the drawer owns visibility).
      window.localStorage.setItem(COLLAPSE_KEY, '1');
      render(
        renderWithProviders(
          <RubricPanel factor={factor} selectedLevelId={null} embedded />,
        ),
      );
      const panel = screen.getByTestId('rubric-panel');
      expect(panel.getAttribute('data-embedded')).toBe('true');
      expect(panel.getAttribute('data-collapsed')).toBe('false');
      // Same level-card markup is reused inside the drawer host.
      expect(screen.getByText('Начальный')).toBeInTheDocument();
      expect(screen.getByText('Продвинутый')).toBeInTheDocument();
    });

    it('drops the sticky + fixed-width chrome and the collapse rail', () => {
      render(
        renderWithProviders(
          <RubricPanel factor={factor} selectedLevelId={null} embedded />,
        ),
      );
      const panel = screen.getByTestId('rubric-panel');
      // No sticky positioning inside the drawer (the drawer scrolls itself).
      expect(panel.className).not.toContain('sticky');
      // No 360px fixed width — the panel fills the drawer.
      expect(panel.getAttribute('style') ?? '').not.toContain('360px');
      // No collapse-to-rail toggles in embedded mode.
      expect(screen.queryByTestId('rubric-toggle')).not.toBeInTheDocument();
      expect(screen.queryByTestId('rubric-toggle-bottom')).not.toBeInTheDocument();
    });

    it('still forwards click-to-score from a level card', () => {
      const onSelectLevel = vi.fn();
      render(
        renderWithProviders(
          <RubricPanel
            factor={factor}
            selectedLevelId={null}
            embedded
            onSelectLevel={onSelectLevel}
          />,
        ),
      );
      fireEvent.click(screen.getByTestId('rubric-level-B'));
      expect(onSelectLevel).toHaveBeenCalledWith('lvl-2');
    });
  });
});
