import { describe, expect, it, vi, beforeEach, afterAll } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import i18n from '@/shared/i18n';
import { renderWithProviders } from '@/test/testUtils';
import { LockEvaluationDialog, type LockSummary } from './LockEvaluationDialog';

const SUMMARY: LockSummary = {
  positionName: 'Senior Risk Analyst',
  positionCode: 'POS-001',
  scoredFactors: 8,
  totalFactors: 8,
  displayedTotal: 742.5,
};

function mount(
  overrides: Partial<React.ComponentProps<typeof LockEvaluationDialog>> = {},
) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    renderWithProviders(
      <LockEvaluationDialog
        open
        summary={SUMMARY}
        onConfirm={onConfirm}
        onCancel={onCancel}
        {...overrides}
      />,
    ),
  );
  return { onConfirm, onCancel };
}

describe('LockEvaluationDialog', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en-US');
  });

  afterAll(async () => {
    await i18n.changeLanguage('ru-RU');
  });

  it('does not render when closed', () => {
    render(
      renderWithProviders(
        <LockEvaluationDialog
          open={false}
          onConfirm={vi.fn()}
          onCancel={vi.fn()}
        />,
      ),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders the review summary from page data (no backend call)', () => {
    mount();
    const summary = screen.getByTestId('lock-summary');
    expect(summary).toHaveTextContent('Senior Risk Analyst');
    expect(summary).toHaveTextContent('8 of 8');
    expect(summary).toHaveTextContent('742.50');
  });

  it('shows the irreversibility / permanence warning', () => {
    mount();
    const warning = screen.getByTestId('lock-irreversible-warning');
    expect(warning.textContent?.toLowerCase()).toContain('permanent');
    expect(warning.textContent?.toLowerCase()).toContain('cannot be undone');
    expect(warning.textContent?.toLowerCase()).toContain('immutable');
  });

  it('keeps Confirm DISABLED until the "I understand" checkbox is ticked', () => {
    const { onConfirm } = mount();
    const confirm = screen.getByTestId('lock-confirm') as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);

    fireEvent.click(confirm);
    expect(onConfirm).not.toHaveBeenCalled();

    fireEvent.click(screen.getByTestId('lock-understand-checkbox'));
    expect(confirm.disabled).toBe(false);
  });

  it('fires onConfirm exactly once after the affirmative gate + confirm', () => {
    const { onConfirm } = mount();
    fireEvent.click(screen.getByTestId('lock-understand-checkbox'));
    fireEvent.click(screen.getByTestId('lock-confirm'));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('cancels via the Cancel button', () => {
    const { onCancel } = mount();
    fireEvent.click(screen.getByTestId('lock-cancel'));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('ESC cancels when not busy', () => {
    const { onCancel } = mount();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('ESC does NOT cancel while busy (pending lock must not be abandoned)', () => {
    const { onCancel } = mount({ busy: true });
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCancel).not.toHaveBeenCalled();
  });

  it('disables both buttons and shows the spinner label while busy', () => {
    mount({ busy: true });
    expect((screen.getByTestId('lock-confirm') as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect((screen.getByTestId('lock-cancel') as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(screen.getByTestId('lock-confirm')).toHaveTextContent('Locking');
  });

  it('surfaces an error message and stays open', () => {
    mount({ error: 'Server rejected the lock' });
    expect(screen.getByTestId('lock-error')).toHaveTextContent(
      'Server rejected the lock',
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('has dialog a11y wiring (role, modal, labelled + described)', () => {
    mount();
    const dialog = screen.getByRole('dialog');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.getAttribute('aria-labelledby')).toBeTruthy();
    expect(dialog.getAttribute('aria-describedby')).toBeTruthy();
  });
});
