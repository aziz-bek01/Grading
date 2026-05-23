import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { AIRecommendationPanel } from './AIRecommendationPanel';

describe('AIRecommendationPanel', () => {
  it('renders the advisory label and disclaimer', () => {
    render(renderWithProviders(<AIRecommendationPanel />));
    const panel = screen.getByTestId('ai-recommendation-panel');
    expect(panel).toBeInTheDocument();
    // Default locale is ru-RU — advisory label uses "подтверждение человеком".
    expect(panel.getAttribute('aria-label')).toMatch(/(human approval|подтверждение человеком|inson tasdig|одам тасдиғи)/i);
    expect(screen.getByTestId('ai-disclaimer')).toBeInTheDocument();
    expect(screen.getByTestId('ai-placeholder')).toBeInTheDocument();
  });

  it('uses the ai-suggestion accent class on the container', () => {
    render(renderWithProviders(<AIRecommendationPanel />));
    const panel = screen.getByTestId('ai-recommendation-panel');
    // Sky/cyan accent border per design-foundation §12.
    expect(panel.className).toMatch(/border-l-ai-suggestion/);
  });
});
