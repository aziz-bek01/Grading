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

  // P1.2 — the placeholder panel (no live AI integration, no handlers) must NOT
  // render the accept/reject controls: inert buttons imply a function that does
  // not exist. They appear only once a real handler is wired (MVP 4).
  it('hides accept/reject when no handlers are wired (placeholder only)', () => {
    render(renderWithProviders(<AIRecommendationPanel />));
    expect(screen.queryByText(/Принять|Accept|Қабул|Qabul/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Отклонить|Reject|Рад этиш|Rad etish/)).not.toBeInTheDocument();
  });

  it('renders accept/reject once a handler is provided (MVP 4 wiring)', () => {
    render(renderWithProviders(<AIRecommendationPanel onAccept={() => {}} />));
    expect(screen.getByText(/Принять|Accept|Қабул|Qabul/)).toBeInTheDocument();
    expect(screen.getByText(/Отклонить|Reject|Рад этиш|Rad etish/)).toBeInTheDocument();
  });
});
