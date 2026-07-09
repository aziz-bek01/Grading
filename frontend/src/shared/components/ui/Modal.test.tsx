import { useRef, useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { Modal } from './Modal';

function Harness({
  onClose,
  dismissible,
  useInitialFocus,
}: {
  onClose: () => void;
  dismissible?: boolean;
  useInitialFocus?: boolean;
}) {
  const confirmRef = useRef<HTMLButtonElement>(null);
  return (
    <div>
      <button type="button" data-testid="outside-trigger">
        outside
      </button>
      <Modal
        open
        onClose={onClose}
        labelledBy="modal-test-title"
        dismissible={dismissible}
        initialFocusRef={useInitialFocus ? confirmRef : undefined}
        data-testid="modal-test"
      >
        <div className="bg-surface rounded-xl border border-border p-6">
          <h2 id="modal-test-title">Test dialog</h2>
          <button type="button" data-testid="first-input">
            first
          </button>
          <button type="button" ref={confirmRef} data-testid="confirm-btn">
            confirm
          </button>
        </div>
      </Modal>
    </div>
  );
}

function mount(overrides: Partial<React.ComponentProps<typeof Harness>> = {}) {
  const onClose = vi.fn();
  render(renderWithProviders(<Harness onClose={onClose} {...overrides} />));
  return { onClose };
}

/** Toggleable host used to exercise open -> close focus-restore behaviour. */
function ToggleHarness() {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" data-testid="opener" onClick={() => setOpen(true)}>
        open
      </button>
      <Modal open={open} onClose={() => setOpen(false)} labelledBy="restore-title">
        <div className="bg-surface rounded-xl border border-border p-6">
          <h2 id="restore-title">Restore test</h2>
          <button type="button" data-testid="close-me" onClick={() => setOpen(false)}>
            close
          </button>
        </div>
      </Modal>
    </div>
  );
}

describe('Modal', () => {
  it('does not render when closed', () => {
    const onClose = vi.fn();
    render(
      renderWithProviders(
        <Modal open={false} onClose={onClose} labelledBy="x">
          <div>content</div>
        </Modal>,
      ),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders with role=dialog, aria-modal, and aria-labelledby wired to the caller heading', () => {
    mount();
    const dialog = screen.getByRole('dialog');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(dialog.getAttribute('aria-labelledby')).toBe('modal-test-title');
  });

  it('Escape closes the modal', () => {
    const { onClose } = mount();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('clicking the backdrop (outside the panel) closes the modal', () => {
    const { onClose } = mount();
    fireEvent.click(screen.getByRole('dialog'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('clicking inside the panel does NOT close the modal', () => {
    const { onClose } = mount();
    fireEvent.click(screen.getByTestId('first-input'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('when dismissible=false, neither Escape nor backdrop-click close the modal', () => {
    const { onClose } = mount({ dismissible: false });
    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.click(screen.getByRole('dialog'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('traps Tab focus within the panel (wraps last -> first)', () => {
    mount();
    const confirmBtn = screen.getByTestId('confirm-btn');
    confirmBtn.focus();
    expect(document.activeElement).toBe(confirmBtn);
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Tab' });
    expect(document.activeElement).toBe(screen.getByTestId('first-input'));
  });

  it('traps Shift+Tab focus within the panel (wraps first -> last)', () => {
    mount();
    const firstInput = screen.getByTestId('first-input');
    firstInput.focus();
    expect(document.activeElement).toBe(firstInput);
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(screen.getByTestId('confirm-btn'));
  });

  it('does not auto-focus anything when initialFocusRef is omitted', () => {
    mount();
    // No forced focus onto a panel control when the caller didn't opt in.
    expect(document.activeElement).not.toBe(screen.getByTestId('confirm-btn'));
    expect(document.activeElement).not.toBe(screen.getByTestId('first-input'));
  });

  it('moves focus to initialFocusRef.current once open (opt-in)', async () => {
    mount({ useInitialFocus: true });
    // The focus call is deferred to a rAF tick inside Modal; flush it.
    await new Promise((resolve) => requestAnimationFrame(resolve));
    expect(document.activeElement).toBe(screen.getByTestId('confirm-btn'));
  });

  it('restores focus to the previously-focused element on close', async () => {
    render(renderWithProviders(<ToggleHarness />));
    const opener = screen.getByTestId('opener');
    opener.focus();
    expect(document.activeElement).toBe(opener);

    fireEvent.click(opener);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('close-me'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.activeElement).toBe(opener);
  });
});
