import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WorkflowStepper } from './WorkflowStepper';
import { WORKFLOW_STAGES, type WorkflowStageProgress } from './workflowTypes';
import { renderWithProviders } from '@/test/testUtils';

const stages: WorkflowStageProgress[] = WORKFLOW_STAGES.map((key, i) => ({
  stage: key,
  status: i === 0 ? 'COMPLETE' : i === 2 ? 'IN_PROGRESS' : i >= 8 ? 'LOCKED_FUTURE' : 'NOT_STARTED',
  completionPercent: i === 0 ? 100 : i === 2 ? 50 : 0,
  sortOrder: i,
}));

describe('<WorkflowStepper />', () => {
  it('renders all 11 stages in canonical order', () => {
    render(renderWithProviders(<WorkflowStepper stages={stages} />));
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBe(11);
    expect(buttons[0]).toHaveAttribute('data-stage', 'SETUP');
    expect(buttons[10]).toHaveAttribute('data-stage', 'ARCHIVE');
  });

  it('marks LOCKED_FUTURE stages as disabled', () => {
    render(renderWithProviders(<WorkflowStepper stages={stages} />));
    const compensationBtn = screen
      .getAllByRole('button')
      .find((b) => b.getAttribute('data-stage') === 'COMPENSATION');
    expect(compensationBtn).toBeDisabled();
  });

  it('marks COMPLETE stages with their status badge', () => {
    render(renderWithProviders(<WorkflowStepper stages={stages} />));
    const setupBtn = screen
      .getAllByRole('button')
      .find((b) => b.getAttribute('data-stage') === 'SETUP');
    expect(setupBtn).toHaveAttribute('data-status', 'COMPLETE');
  });

  it('fires onSelect when a non-locked stage is clicked', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<WorkflowStepper stages={stages} onSelect={onSelect} />));
    const positionsBtn = screen
      .getAllByRole('button')
      .find((b) => b.getAttribute('data-stage') === 'POSITIONS')!;
    await user.click(positionsBtn);
    expect(onSelect).toHaveBeenCalledWith('POSITIONS');
  });
});
