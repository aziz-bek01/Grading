import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { EvaluationMatrix } from './EvaluationMatrix';
import type {
  Factor,
  MethodologyVersion,
} from '@/features/methodology/types';
import type { EvaluationScore } from '../types';

function makeFactor(code: string, required = true): Factor {
  return {
    id: `f-${code}`,
    methodology_version_id: 'v-1',
    code,
    name_i18n: { 'ru-RU': code, 'en-US': code },
    weight: 10,
    max_points: 100,
    sort_order: 0,
    required,
    levels: [
      { id: `f-${code}-l1`, factor_id: `f-${code}`, code: 'A', level_order: 0, points: 25, scale_value: 0.25, label_i18n: { 'ru-RU': 'A', 'en-US': 'A' } },
      { id: `f-${code}-l2`, factor_id: `f-${code}`, code: 'B', level_order: 1, points: 75, scale_value: 0.75, label_i18n: { 'ru-RU': 'B', 'en-US': 'B' } },
    ],
  };
}

const version: MethodologyVersion = {
  id: 'v-1',
  methodology_id: 'm-1',
  project_id: 'p-1',
  version_number: 1,
  status: 'APPROVED',
  scoring_mode: 'WEIGHTED_POINTS',
  target_total_points: 100,
  factors: [makeFactor('F1'), makeFactor('F2')],
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
};

describe('EvaluationMatrix', () => {
  it('renders factors + levels as radio buttons when editable', () => {
    render(
      renderWithProviders(
        <EvaluationMatrix
          version={version}
          scores={[]}
          status="DRAFT"
          onSelectLevel={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('factor-row-F1')).toBeInTheDocument();
    expect(screen.getByTestId('factor-row-F2')).toBeInTheDocument();
    // Buttons with role=radio (editable).
    expect(screen.getByTestId('level-F1-A').tagName).toBe('BUTTON');
  });

  it('shows required indicator for unfilled required factors', () => {
    render(
      renderWithProviders(
        <EvaluationMatrix
          version={version}
          scores={[]}
          status="DRAFT"
          onSelectLevel={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('factor-required-F1')).toBeInTheDocument();
    expect(screen.getByTestId('missing-required-banner')).toBeInTheDocument();
  });

  it('calls onSelectLevel and updates UI optimistically on click', () => {
    const onSelectLevel = vi.fn();
    render(
      renderWithProviders(
        <EvaluationMatrix
          version={version}
          scores={[]}
          status="DRAFT"
          onSelectLevel={onSelectLevel}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('level-F1-B'));
    expect(onSelectLevel).toHaveBeenCalledWith('f-F1', 'f-F1-l2');
    const btn = screen.getByTestId('level-F1-B');
    expect(btn.getAttribute('aria-checked')).toBe('true');
  });

  it('renders level markers as DIV (not disabled input) for APPROVED status — read-only', () => {
    const scores: EvaluationScore[] = [
      {
        id: 's-1',
        evaluation_id: 'e-1',
        factor_id: 'f-F1',
        factor_level_id: 'f-F1-l2',
        raw_factor_score: 7.5,
        manually_adjusted: false,
      },
    ];
    render(
      renderWithProviders(
        <EvaluationMatrix
          version={version}
          scores={scores}
          status="APPROVED"
          onSelectLevel={() => {}}
        />,
      ),
    );
    const marker = screen.getByTestId('level-marker-F1-B');
    expect(marker.tagName).toBe('DIV');
    expect(marker.getAttribute('data-readonly')).toBe('true');
    // No button-as-radio in read-only mode.
    expect(screen.queryByTestId('level-F1-A')).not.toBeInTheDocument();
  });

  it('renders the preview-only disclaimer', () => {
    render(
      renderWithProviders(
        <EvaluationMatrix
          version={version}
          scores={[]}
          status="DRAFT"
          onSelectLevel={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('preview-only-disclaimer')).toBeInTheDocument();
  });
});
