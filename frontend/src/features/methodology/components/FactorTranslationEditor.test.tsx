import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { FactorTranslationEditor } from './FactorTranslationEditor';
import type { Factor } from '../types';

function f(code: string, names: Partial<Record<string, string>>): Factor {
  return {
    id: `f-${code}`,
    methodology_version_id: 'v1',
    code,
    name_i18n: names,
    weight: 0,
    max_points: 0,
    sort_order: 0,
    required: true,
    levels: [],
  };
}

describe('FactorTranslationEditor', () => {
  it('renders N factors × 4 locale columns', () => {
    const factors: Factor[] = [
      f('A', { 'ru-RU': 'А', 'en-US': 'A', 'uz-Cyrl-UZ': 'А', 'uz-Latn-UZ': 'A' }),
      f('B', { 'ru-RU': 'Б', 'en-US': 'B' }),
    ];
    render(renderWithProviders(<FactorTranslationEditor factors={factors} />));
    // 4 completeness cards.
    expect(screen.getByTestId('completeness-ru-RU')).toBeInTheDocument();
    expect(screen.getByTestId('completeness-en-US')).toBeInTheDocument();
    expect(screen.getByTestId('completeness-uz-Cyrl-UZ')).toBeInTheDocument();
    expect(screen.getByTestId('completeness-uz-Latn-UZ')).toBeInTheDocument();

    // ru-RU: 2/2 = 100%; uz-Cyrl-UZ: 1/2 = 50%
    expect(screen.getByTestId('completeness-ru-RU').textContent).toContain('100%');
    expect(screen.getByTestId('completeness-uz-Cyrl-UZ').textContent).toContain('50%');
  });

  it('renders editable cells when not read-only', () => {
    const factors: Factor[] = [f('A', { 'ru-RU': 'А' })];
    render(renderWithProviders(<FactorTranslationEditor factors={factors} />));
    expect(screen.getByTestId('translation-cell-A-ru-RU')).toBeInTheDocument();
  });

  it('renders read-only spans when readOnly', () => {
    const factors: Factor[] = [f('A', { 'ru-RU': 'А' })];
    render(renderWithProviders(<FactorTranslationEditor factors={factors} readOnly />));
    expect(screen.queryByTestId('translation-cell-A-ru-RU')).toBeNull();
    expect(screen.getByTestId('translation-readonly-A-ru-RU')).toBeInTheDocument();
  });
});
