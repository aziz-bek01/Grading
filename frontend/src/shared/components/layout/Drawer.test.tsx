import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { Drawer } from './Drawer';

afterEach(() => {
  // The drawer locks body scroll while open; ensure each test starts clean.
  document.body.style.overflow = '';
});

describe('Drawer (generic slide-over host)', () => {
  it('renders nothing while closed', () => {
    render(
      renderWithProviders(
        <Drawer open={false} title="Rubric" onClose={() => {}}>
          <p>panel body</p>
        </Drawer>,
      ),
    );
    expect(screen.queryByText('panel body')).not.toBeInTheDocument();
  });

  it('renders the title + children when open and locks body scroll', () => {
    render(
      renderWithProviders(
        <Drawer open title="Rubric" onClose={() => {}} data-testid="d">
          <p>panel body</p>
        </Drawer>,
      ),
    );
    expect(screen.getByTestId('d')).toBeInTheDocument();
    expect(screen.getByText('Rubric')).toBeInTheDocument();
    expect(screen.getByText('panel body')).toBeInTheDocument();
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('closes on overlay click and on Escape', () => {
    const onClose = vi.fn();
    render(
      renderWithProviders(
        <Drawer open title="Rubric" onClose={onClose} data-testid="d">
          <p>panel body</p>
        </Drawer>,
      ),
    );
    // Overlay click (the dialog root receives the click target).
    fireEvent.click(screen.getByTestId('d'));
    expect(onClose).toHaveBeenCalledTimes(1);
    // Escape key.
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
