import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ScorePreviewBanner } from './ScorePreviewBanner';

describe('ScorePreviewBanner', () => {
  it('always renders the "Preview only" disclaimer', () => {
    render(
      renderWithProviders(<ScorePreviewBanner rawTotal={123} displayedTotal={123.45} />),
    );
    // Disclaimer must always be visible regardless of total state.
    const disclaimer = screen.getByTestId('preview-only-disclaimer');
    expect(disclaimer.textContent?.toLowerCase()).toMatch(/backend|бэкенд|backend/i);
  });

  it('renders "—" when total is null', () => {
    render(renderWithProviders(<ScorePreviewBanner rawTotal={null} displayedTotal={null} />));
    expect(screen.getByTestId('preview-displayed-total').textContent).toBe('—');
  });

  it('formats displayed total with 2 decimals', () => {
    render(
      renderWithProviders(<ScorePreviewBanner rawTotal={750.1234} displayedTotal={750.12} />),
    );
    expect(screen.getByTestId('preview-displayed-total').textContent).toBe('750.12');
    expect(screen.getByTestId('preview-raw-total').textContent).toBe('750.1234');
  });
});
