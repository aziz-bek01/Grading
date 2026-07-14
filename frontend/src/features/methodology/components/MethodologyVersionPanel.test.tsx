import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/testUtils';
import { MethodologyVersionPanel } from './MethodologyVersionPanel';
import type { MethodologyVersionSummary } from '../types';

const versions: MethodologyVersionSummary[] = [
  {
    id: 'v-1',
    version_number: 1,
    status: 'APPROVED',
    scoring_mode: 'WEIGHTED_POINTS',
    updated_at: '2026-01-01T00:00:00Z',
  },
];

describe('<MethodologyVersionPanel />', () => {
  it('renders the "Compare versions" affordance as aria-disabled (not native disabled) so it stays focusable', () => {
    render(
      renderWithProviders(
        <MethodologyVersionPanel versions={versions} activeVersionId="v-1" onSelect={vi.fn()} />,
      ),
    );
    const compareBtn = screen.getByTestId('methodology-compare-versions');
    expect(compareBtn).toHaveAttribute('aria-disabled', 'true');
    expect(compareBtn).not.toBeDisabled();
  });

  it('explains the MVP 2 gate via an accessible tooltip on hover', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <MethodologyVersionPanel versions={versions} activeVersionId="v-1" onSelect={vi.fn()} />,
      ),
    );
    const compareBtn = screen.getByTestId('methodology-compare-versions');
    expect(screen.queryByRole('tooltip')).toBeNull();

    await user.hover(compareBtn);
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('Сравнение версий появится в MVP 2.');
    expect(compareBtn).toHaveAttribute('aria-describedby', tooltip.id);
  });

  it('explains the MVP 2 gate via the same tooltip on keyboard focus', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <MethodologyVersionPanel versions={versions} activeVersionId="v-1" onSelect={vi.fn()} />,
      ),
    );
    // First tab stop is the version-row button; the compare-versions
    // affordance is rendered right after the version list.
    await user.tab();
    await user.tab();
    expect(screen.getByTestId('methodology-compare-versions')).toHaveFocus();
    expect(screen.getByRole('tooltip')).toHaveTextContent('Сравнение версий появится в MVP 2.');
  });
});
