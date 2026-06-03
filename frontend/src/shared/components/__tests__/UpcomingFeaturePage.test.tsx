import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { UpcomingFeaturePage } from '../UpcomingFeaturePage';

describe('<UpcomingFeaturePage />', () => {
  it('renders the title, sprint badge, MVP tag and one bullet per featuresKey', () => {
    render(
      renderWithProviders(
        <UpcomingFeaturePage
          titleKey="upcoming.compensation.title"
          subtitleKey="upcoming.compensation.subtitle"
          featuresKeys={[
            'upcoming.compensation.features.1',
            'upcoming.compensation.features.2',
            'upcoming.compensation.features.3',
            'upcoming.compensation.features.4',
            'upcoming.compensation.features.5',
          ]}
          targetSprintLabel="Sprint 14 · 06.07-20.07.2026"
          targetDate="2026-07-06"
          mvpPhase="MVP 3 Phase 1"
        />,
      ),
    );

    // Sprint badge contains the human-readable label + the <time datetime=…>.
    const badge = screen.getByTestId('upcoming-sprint-badge');
    expect(badge).toHaveTextContent('Sprint 14 · 06.07-20.07.2026');
    expect(badge.querySelector('time')).toHaveAttribute('datetime', '2026-07-06');

    // MVP phase pill.
    expect(screen.getByText('MVP 3 Phase 1')).toBeInTheDocument();

    // Five bullet items rendered (one per featuresKey).
    const list = screen.getByRole('list');
    expect(list.querySelectorAll('li')).toHaveLength(5);
  });
});
