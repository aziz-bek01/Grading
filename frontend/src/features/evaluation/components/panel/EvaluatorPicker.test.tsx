import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { EvaluatorPicker } from './EvaluatorPicker';

const users = [
  { id: 'u1', full_name: 'Evaluator One', status: 'ACTIVE' as const },
  { id: 'u2', full_name: 'Evaluator Two', status: 'ACTIVE' as const },
];

vi.mock('@/features/users-access/hooks/useUsers', () => ({
  useAllUsers: () => ({
    data: { items: users, totalElements: 21, truncated: true },
    isLoading: false,
    isError: false,
  }),
}));

/**
 * Covers the `dataTable.results_truncated` banner's migration from an ad-hoc
 * `<p role="status">` to the shared `InlineBanner` (dedupe sweep) — same
 * message/testid/role, now rendered through the shared primitive.
 */
describe('<EvaluatorPicker /> truncated banner (single mode)', () => {
  it('renders the results_truncated InlineBanner as a polite status, not an alert', () => {
    render(
      renderWithProviders(
        <EvaluatorPicker mode="single" selectId="evaluator-select" value="" onChange={vi.fn()} />,
      ),
    );
    const banner = screen.getByTestId('evaluator-select-truncated');
    expect(banner).toHaveAttribute('role', 'status');
    expect(banner).toHaveTextContent('Показаны первые 2 из 21');
  });
});

describe('<EvaluatorPicker /> truncated banner (multi mode)', () => {
  it('renders the results_truncated InlineBanner as a polite status, not an alert', () => {
    render(
      renderWithProviders(
        <EvaluatorPicker
          mode="multi"
          testIdPrefix="evaluator-multi"
          value={[]}
          onChange={vi.fn()}
        />,
      ),
    );
    const banner = screen.getByTestId('evaluator-multi-truncated');
    expect(banner).toHaveAttribute('role', 'status');
    expect(banner).toHaveTextContent('Показаны первые 2 из 21');
  });
});
