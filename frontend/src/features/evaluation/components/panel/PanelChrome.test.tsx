import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import i18n from '@/shared/i18n';
import { renderWithProviders } from '@/test/testUtils';
import { PanelRoleChip } from './PanelRoleChip';
import { PanelBlindBanner } from './PanelBlindBanner';

describe('Panel chrome — role chip + blind banner (FE-3 / FE-8)', () => {
  afterEach(() => {
    i18n.changeLanguage('ru-RU');
  });

  it('renders a localized role chip and the "You:" self prefix', () => {
    render(renderWithProviders(<PanelRoleChip role="EXTERNAL_EXPERT" self />));
    const chip = screen.getByTestId('panel-role-chip-EXTERNAL_EXPERT');
    // ru-RU label with self prefix.
    expect(chip).toHaveTextContent('Вы: Внешний эксперт');
  });

  it('renders the blind banner text', () => {
    render(renderWithProviders(<PanelBlindBanner />));
    expect(screen.getByTestId('panel-blind-banner')).toBeInTheDocument();
  });

  it('localizes role labels across all 4 locales (AC-17)', async () => {
    const expected: Record<string, string> = {
      'ru-RU': 'Внешний эксперт',
      'uz-Cyrl-UZ': 'Ташқи эксперт',
      'uz-Latn-UZ': 'Tashqi ekspert',
      'en-US': 'External expert',
    };
    for (const [locale, label] of Object.entries(expected)) {
      await i18n.changeLanguage(locale);
      const { unmount } = render(
        renderWithProviders(<PanelRoleChip role="EXTERNAL_EXPERT" />),
      );
      expect(screen.getByTestId('panel-role-chip-EXTERNAL_EXPERT')).toHaveTextContent(
        label,
      );
      unmount();
    }
  });
});
