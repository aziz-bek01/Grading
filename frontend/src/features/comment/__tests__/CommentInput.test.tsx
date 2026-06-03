import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/testUtils';
import { CommentInput } from '../components/CommentInput';

describe('<CommentInput />', () => {
  it('renders counter starting at 0/5000', () => {
    const onSubmit = vi.fn();
    render(renderWithProviders(<CommentInput onSubmit={onSubmit} />));
    expect(screen.getByTestId('comment-counter').textContent).toMatch(/0\s*\/\s*5000/);
  });

  it('shows @ mention hint when user types @', async () => {
    const onSubmit = vi.fn();
    render(renderWithProviders(<CommentInput onSubmit={onSubmit} />));
    const user = userEvent.setup();
    const textarea = screen.getByRole('textbox');
    await user.type(textarea, 'Hello @user');
    expect(screen.getByText(/mention|упоминания|belgilash/i)).toBeInTheDocument();
  });

  it('enforces 5000 char max via maxLength attribute', () => {
    const onSubmit = vi.fn();
    render(renderWithProviders(<CommentInput onSubmit={onSubmit} />));
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.maxLength).toBe(5000);
  });

  it('submits the entered text', async () => {
    const onSubmit = vi.fn();
    render(renderWithProviders(<CommentInput onSubmit={onSubmit} />));
    const user = userEvent.setup();
    await user.type(screen.getByRole('textbox'), 'A real comment body');
    await user.click(screen.getByRole('button', { name: /submit|Юбориш|Yuborish|Отправить/i }));
    expect(onSubmit).toHaveBeenCalledWith('A real comment body');
  });
});
