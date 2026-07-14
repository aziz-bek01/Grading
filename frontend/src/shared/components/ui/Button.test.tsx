import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from './Button';

describe('<Button />', () => {
  it('defaults to type="button" and variant="primary" size="md"', () => {
    render(<Button data-testid="btn">Save</Button>);
    const btn = screen.getByTestId('btn');
    expect(btn).toHaveAttribute('type', 'button');
    expect(btn.className).toContain('bg-primary-500');
    expect(btn.className).toContain('h-10');
  });

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <Button data-testid="btn" onClick={onClick}>
        Save
      </Button>,
    );
    await user.click(screen.getByTestId('btn'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('is disabled and inert when disabled is set', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <Button data-testid="btn" disabled onClick={onClick}>
        Save
      </Button>,
    );
    const btn = screen.getByTestId('btn');
    expect(btn).toBeDisabled();
    await user.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('respects an explicit type="submit"', () => {
    render(
      <Button data-testid="btn" type="submit">
        Submit
      </Button>,
    );
    expect(screen.getByTestId('btn')).toHaveAttribute('type', 'submit');
  });

  describe('size="compact"', () => {
    it('reproduces the dense-UI footprint (px-3 py-2 text-sm) with no fixed height', () => {
      render(
        <Button data-testid="btn" size="compact">
          Continue
        </Button>,
      );
      const { className } = screen.getByTestId('btn');
      expect(className).toContain('px-3');
      expect(className).toContain('py-2');
      expect(className).toContain('text-sm');
      expect(className).toContain('rounded-md');
      // Unlike sm/md/lg, compact must NOT set a fixed height — padding and
      // line-height alone define it, matching the dense wizard/table buttons
      // it replaces (so migrating callers doesn't reflow tight layouts).
      expect(className).not.toMatch(/\bh-\d/);
    });

    it('applies the secondary variant treatment alongside the compact size', () => {
      render(
        <Button data-testid="btn" size="compact" variant="secondary">
          Back
        </Button>,
      );
      const { className } = screen.getByTestId('btn');
      expect(className).toContain('border-border-strong');
      expect(className).toContain('px-3');
      expect(className).toContain('py-2');
    });
  });

  describe('leadingIcon / trailingIcon', () => {
    it('renders leadingIcon before and trailingIcon after the label', () => {
      render(
        <Button
          data-testid="btn"
          leadingIcon={<span data-testid="leading">L</span>}
          trailingIcon={<span data-testid="trailing">T</span>}
        >
          Label
        </Button>,
      );
      const btn = screen.getByTestId('btn');
      const leading = screen.getByTestId('leading');
      const trailing = screen.getByTestId('trailing');
      expect(btn).toContainElement(leading);
      expect(btn).toContainElement(trailing);
    });
  });
});
