import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pencil } from 'lucide-react';
import { IconButton } from './IconButton';

describe('<IconButton />', () => {
  it('renders the accessible name as both aria-label and title', () => {
    render(
      <IconButton label="Edit" onClick={vi.fn()}>
        <Pencil size={15} aria-hidden />
      </IconButton>,
    );
    const btn = screen.getByRole('button', { name: 'Edit' });
    expect(btn).toHaveAttribute('title', 'Edit');
  });

  it('wires data-testid through to the button element', () => {
    render(
      <IconButton label="Edit" testId="row-edit-1" onClick={vi.fn()}>
        <Pencil size={15} aria-hidden />
      </IconButton>,
    );
    expect(screen.getByTestId('row-edit-1')).toBeInTheDocument();
  });

  it('calls onClick with the click event', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <IconButton label="Edit" testId="row-edit-1" onClick={onClick}>
        <Pencil size={15} aria-hidden />
      </IconButton>,
    );
    await user.click(screen.getByTestId('row-edit-1'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('applies the danger color treatment when danger is set', () => {
    render(
      <IconButton label="Archive" testId="row-archive-1" danger onClick={vi.fn()}>
        <Pencil size={15} aria-hidden />
      </IconButton>,
    );
    expect(screen.getByTestId('row-archive-1').className).toContain('text-danger-700');
  });

  it('does not apply the danger treatment by default', () => {
    render(
      <IconButton label="Edit" testId="row-edit-1" onClick={vi.fn()}>
        <Pencil size={15} aria-hidden />
      </IconButton>,
    );
    expect(screen.getByTestId('row-edit-1').className).not.toContain('text-danger-700');
  });

  it('is disabled and inert when disabled is set', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(
      <IconButton label="Edit" testId="row-edit-1" disabled onClick={onClick}>
        <Pencil size={15} aria-hidden />
      </IconButton>,
    );
    const btn = screen.getByTestId('row-edit-1');
    expect(btn).toBeDisabled();
    await user.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });
});
