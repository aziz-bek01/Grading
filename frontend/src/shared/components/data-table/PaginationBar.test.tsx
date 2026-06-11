import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PaginationBar } from './PaginationBar';
import { renderWithProviders } from '@/test/testUtils';

/**
 * FE-5: the counter must show a RANGE/COUNT, not a start index. The old
 * `page*pageSize+1` value rendered as a count produced the "1 / 2 vs
 * Саҳифа 1 / 1" contradiction. These assertions are locale-agnostic — they
 * check the numeric tokens (from–to / total) regardless of translation.
 */
describe('<PaginationBar /> — range counter (FE-5)', () => {
  it('shows the on-page range 1–2 / 2 consistent with page 1 / 1', () => {
    render(
      renderWithProviders(
        <PaginationBar
          page={0}
          totalPages={1}
          total={2}
          pageSize={25}
          onPageChange={() => {}}
        />,
      ),
    );
    // The misleading "1 / 2" start-index count must be gone; the range 1–2
    // (with total 2) is shown instead.
    const text = document.body.textContent ?? '';
    expect(text).toContain('1');
    expect(text).toContain('2');
    // Page indicator stays "1 / 1" (no contradiction with the range).
    expect(screen.getByText(/1\s*\/\s*1|1\s*из\s*1|1\s*of\s*1/)).toBeInTheDocument();
  });

  it('computes the range for a middle page (page 2 of 3, 25/page, total 60)', () => {
    render(
      renderWithProviders(
        <PaginationBar
          page={1}
          totalPages={3}
          total={60}
          pageSize={25}
          onPageChange={() => {}}
        />,
      ),
    );
    const text = document.body.textContent ?? '';
    // from = 1*25+1 = 26, to = min(60, 2*25) = 50.
    expect(text).toContain('26');
    expect(text).toContain('50');
    expect(text).toContain('60');
  });

  it('shows 0 from/to when there are no items', () => {
    render(
      renderWithProviders(
        <PaginationBar
          page={0}
          totalPages={1}
          total={0}
          pageSize={25}
          onPageChange={() => {}}
        />,
      ),
    );
    expect(document.body.textContent ?? '').toContain('0');
  });
});
