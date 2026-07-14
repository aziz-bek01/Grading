import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/testUtils';
import { Tooltip } from './Tooltip';

describe('<Tooltip />', () => {
  it('does not render the tooltip content until hovered or focused', () => {
    render(
      renderWithProviders(
        <Tooltip content="Explains why the button is disabled">
          <button type="button">Compare versions</button>
        </Tooltip>,
      ),
    );
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('shows the tooltip on hover and wires aria-describedby onto the trigger', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <Tooltip content="Explains why the button is disabled">
          <button type="button">Compare versions</button>
        </Tooltip>,
      ),
    );
    const trigger = screen.getByRole('button', { name: 'Compare versions' });
    await user.hover(trigger);

    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('Explains why the button is disabled');
    expect(trigger).toHaveAttribute('aria-describedby', tooltip.id);

    await user.unhover(trigger);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('shows the tooltip on keyboard focus', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <Tooltip content="Coming in a later MVP">
          <button type="button">Compare versions</button>
        </Tooltip>,
      ),
    );
    await user.tab();
    expect(screen.getByRole('button', { name: 'Compare versions' })).toHaveFocus();
    expect(screen.getByRole('tooltip')).toHaveTextContent('Coming in a later MVP');
  });

  it('hides the tooltip on Escape while keeping focus on the trigger', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <Tooltip content="Coming in a later MVP">
          <button type="button">Compare versions</button>
        </Tooltip>,
      ),
    );
    await user.tab();
    const trigger = screen.getByRole('button', { name: 'Compare versions' });
    expect(trigger).toHaveFocus();
    expect(screen.getByRole('tooltip')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('tooltip')).toBeNull();
    expect(trigger).toHaveFocus();
  });

  it('hides the tooltip on blur', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <div>
          <Tooltip content="Coming in a later MVP">
            <button type="button">Compare versions</button>
          </Tooltip>
          <button type="button">Elsewhere</button>
        </div>,
      ),
    );
    await user.tab();
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    await user.tab();
    expect(screen.queryByRole('tooltip')).toBeNull();
  });
});
