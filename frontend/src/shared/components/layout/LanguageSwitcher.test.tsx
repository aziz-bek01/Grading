import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LanguageSwitcher } from './LanguageSwitcher';
import { renderWithProviders } from '@/test/testUtils';
import i18n from '@/shared/i18n';

describe('<LanguageSwitcher />', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('ru-RU');
  });

  // Non-default locale bundles are code-split (P1-T1) so the switch resolves
  // asynchronously once the lazy chunk loads — assert via `waitFor`.
  it('starts in RU and switches to UZ-Latn then EN', async () => {
    const user = userEvent.setup();
    render(renderWithProviders(<LanguageSwitcher />));

    expect(screen.getByTestId('lang-pill')).toHaveTextContent('RU');

    await user.click(screen.getByRole('button'));
    await user.click(screen.getByRole('option', { name: /Oʻzbek \(lotin\)/i }));
    await waitFor(() => expect(i18n.language).toBe('uz-Latn-UZ'));
    expect(screen.getByTestId('lang-pill')).toHaveTextContent('UZ');

    await user.click(screen.getByRole('button'));
    await user.click(screen.getByRole('option', { name: /English/i }));
    await waitFor(() => expect(i18n.language).toBe('en-US'));
    expect(screen.getByTestId('lang-pill')).toHaveTextContent('EN');
  });
});
