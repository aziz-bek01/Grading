import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ApprovalRequestCard } from '../components/ApprovalRequestCard';
import type { ApprovalRequestSummary } from '../types';

/**
 * Cross-project global inbox fix: the card must show WHICH project a request
 * belongs to via the BE-resolved `projectLabel` (mirrors `entityLabel`), and
 * must never render the line when the backend omits it (NON_NULL / unresolved
 * project). Tests run in ru-RU (default product locale).
 */
const baseRequest: ApprovalRequestSummary = {
  id: 'appr-1',
  projectId: 'proj-acme-2026',
  entityType: 'EVALUATION',
  entityId: 'eval-swe-1',
  entityLabel: { 'ru-RU': 'Старший разработчик · CFO Finance v1' },
  status: 'PENDING',
  initiatedByUserId: 'user-initiator',
  initiatedByName: 'HRLab Consultant',
  initiatedAt: '2026-05-15T08:30:00Z',
  currentStepOrder: 1,
  totalSteps: 2,
};

describe('<ApprovalRequestCard /> project label', () => {
  it('renders a localized "Project · <name>" line when projectLabel is present', () => {
    render(
      renderWithProviders(
        <ApprovalRequestCard
          request={{
            ...baseRequest,
            projectLabel: { 'ru-RU': 'Acme HRTech 2026', 'en-US': 'Acme HRTech 2026' },
          }}
        />,
      ),
    );
    const projectLine = screen.getByTestId('approval-card-project-label');
    expect(projectLine).toHaveTextContent('Проект · Acme HRTech 2026');
  });

  it('omits the project label line when projectLabel is absent', () => {
    render(renderWithProviders(<ApprovalRequestCard request={baseRequest} />));
    expect(screen.queryByTestId('approval-card-project-label')).toBeNull();
  });

  it('still renders the existing entity label and card testid unchanged', () => {
    render(renderWithProviders(<ApprovalRequestCard request={baseRequest} />));
    expect(screen.getByTestId('approval-request-card')).toBeInTheDocument();
    expect(screen.getByText('Старший разработчик · CFO Finance v1')).toBeInTheDocument();
  });
});
