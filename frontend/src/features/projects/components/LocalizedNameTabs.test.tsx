import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/testUtils';
import { LocalizedNameTabs } from './LocalizedNameTabs';
import type { LocalizedString } from '@/shared/types/common';

/**
 * Per-tab fill/empty indicator (i18n hardening, Finding 3).
 *
 * The indicator is a UX affordance only — it must never change validation
 * semantics (still driven entirely by the caller's `error` prop) or the tab
 * switching behavior (still controlled by clicking a tab / `role="tab"`).
 */
describe('<LocalizedNameTabs /> fill indicator', () => {
  function renderTabs(value: LocalizedString) {
    const onChange = vi.fn();
    render(renderWithProviders(<LocalizedNameTabs value={value} onChange={onChange} />));
    return { onChange };
  }

  it('marks a locale with a non-empty value as filled (accessible name + data attribute)', () => {
    renderTabs({ 'ru-RU': 'Проект 2026' });
    const indicator = screen.getByTestId('locale-tab-indicator-ru-RU');
    expect(indicator).toHaveAttribute('data-filled', 'true');
    expect(indicator).toHaveTextContent(/заполнен/i);
  });

  it('marks a locale with an empty/whitespace-only value as empty', () => {
    renderTabs({ 'ru-RU': 'Проект 2026', 'en-US': '   ' });
    const filled = screen.getByTestId('locale-tab-indicator-ru-RU');
    const empty = screen.getByTestId('locale-tab-indicator-en-US');
    expect(filled).toHaveAttribute('data-filled', 'true');
    expect(empty).not.toHaveAttribute('data-filled');
    expect(empty).toHaveTextContent(/не заполнен/i);
  });

  it('marks every locale as empty when value is entirely missing', () => {
    renderTabs({});
    for (const loc of ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US']) {
      expect(screen.getByTestId(`locale-tab-indicator-${loc}`)).not.toHaveAttribute('data-filled');
    }
  });

  it('updates the indicator live as the active locale is typed into', async () => {
    const user = userEvent.setup();
    function Wrapper() {
      const [value, setValue] = useState<LocalizedString>({});
      return <LocalizedNameTabs value={value} onChange={setValue} />;
    }
    render(renderWithProviders(<Wrapper />));
    expect(screen.getByTestId('locale-tab-indicator-ru-RU')).not.toHaveAttribute('data-filled');
    await user.type(screen.getByTestId('locale-input-ru-RU'), 'Название');
    expect(screen.getByTestId('locale-tab-indicator-ru-RU')).toHaveAttribute('data-filled', 'true');
  });

  it('still switches tabs on click (tab-switching behavior unchanged)', async () => {
    const user = userEvent.setup();
    renderTabs({ 'ru-RU': 'Название' });
    expect(screen.getByTestId('locale-input-ru-RU')).not.toHaveAttribute('hidden');
    await user.click(screen.getByRole('tab', { name: /English/i }));
    expect(screen.getByTestId('locale-input-en-US')).not.toHaveAttribute('hidden');
    expect(screen.getByTestId('locale-input-ru-RU')).toHaveAttribute('hidden');
  });

  it('does not change validation: the error prop still renders regardless of fill state', () => {
    renderTabs({});
    render(
      renderWithProviders(
        <LocalizedNameTabs value={{}} onChange={vi.fn()} error="Обязательное поле" />,
      ),
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Обязательное поле');
  });
});
