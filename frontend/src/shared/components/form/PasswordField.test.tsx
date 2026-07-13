import { useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PasswordField, type PasswordFieldProps } from './PasswordField';

type Harness = Omit<
  PasswordFieldProps,
  'registration' | 'showPassword' | 'onToggleShow' | 'onGenerate'
> & {
  showPassword?: boolean;
  onToggleShow?: () => void;
  onGenerate?: () => void;
};

function Wrapper(props: Harness) {
  const { register } = useForm<{ password: string }>({ defaultValues: { password: '' } });
  return (
    <PasswordField
      {...props}
      showPassword={props.showPassword ?? false}
      onToggleShow={props.onToggleShow ?? vi.fn()}
      onGenerate={props.onGenerate ?? vi.fn()}
      registration={register('password')}
    />
  );
}

const baseProps: Harness = {
  id: 'pw',
  label: 'Password',
  showLabel: 'Show password',
  hideLabel: 'Hide password',
  generateLabel: 'Generate',
  testId: 'pw-input',
  toggleTestId: 'pw-toggle',
  generateTestId: 'pw-generate',
};

describe('<PasswordField />', () => {
  it('renders masked input by default, no required asterisk when required is unset', () => {
    render(<Wrapper {...baseProps} />);
    const input = screen.getByTestId('pw-input') as HTMLInputElement;
    expect(input.type).toBe('password');
    expect(screen.queryByText('*')).not.toBeInTheDocument();
  });

  it('shows a required asterisk when required is set', () => {
    render(<Wrapper {...baseProps} required />);
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  it('toggle button uses the show/hide labels and calls onToggleShow', async () => {
    const onToggleShow = vi.fn();
    const user = userEvent.setup();
    render(<Wrapper {...baseProps} onToggleShow={onToggleShow} />);
    const toggle = screen.getByTestId('pw-toggle');
    expect(toggle).toHaveAttribute('aria-label', 'Show password');
    await user.click(toggle);
    expect(onToggleShow).toHaveBeenCalledTimes(1);
  });

  it('reveals the password as text when showPassword is true, with the hide label', () => {
    render(<Wrapper {...baseProps} showPassword />);
    const input = screen.getByTestId('pw-input') as HTMLInputElement;
    expect(input.type).toBe('text');
    expect(screen.getByTestId('pw-toggle')).toHaveAttribute('aria-label', 'Hide password');
  });

  it('generate button fires onGenerate', async () => {
    const onGenerate = vi.fn();
    const user = userEvent.setup();
    render(<Wrapper {...baseProps} onGenerate={onGenerate} />);
    await user.click(screen.getByTestId('pw-generate'));
    expect(onGenerate).toHaveBeenCalledTimes(1);
  });

  it('renders the hint and an inline error when provided', () => {
    render(<Wrapper {...baseProps} hint="Min 8 chars" error="Too weak" />);
    expect(screen.getByText('Min 8 chars')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Too weak');
  });

  it('renders no error alert when error is omitted', () => {
    render(<Wrapper {...baseProps} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
