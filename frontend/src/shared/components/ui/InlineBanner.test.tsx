import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/testUtils';
import { InlineBanner } from './InlineBanner';

describe('<InlineBanner />', () => {
  it('defaults to role="status" for the info variant', () => {
    render(renderWithProviders(<InlineBanner variant="info">Heads up</InlineBanner>));
    expect(screen.getByRole('status')).toHaveTextContent('Heads up');
  });

  it('defaults to role="status" for the success variant', () => {
    render(renderWithProviders(<InlineBanner variant="success">Saved</InlineBanner>));
    expect(screen.getByRole('status')).toHaveTextContent('Saved');
  });

  it('defaults to role="alert" for the warning variant', () => {
    render(renderWithProviders(<InlineBanner variant="warning">Careful</InlineBanner>));
    expect(screen.getByRole('alert')).toHaveTextContent('Careful');
  });

  it('lets the caller override the default role (e.g. a non-blocking warning-toned FYI)', () => {
    render(
      renderWithProviders(
        <InlineBanner variant="warning" role="status">
          Results truncated
        </InlineBanner>,
      ),
    );
    expect(screen.getByRole('status')).toHaveTextContent('Results truncated');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('renders a variant-appropriate default icon that is hidden from assistive tech', () => {
    const { container } = render(
      renderWithProviders(<InlineBanner variant="warning">Careful</InlineBanner>),
    );
    expect(container.querySelector('svg[aria-hidden]')).not.toBeNull();
  });

  it('omits the icon entirely when icon={null} is passed', () => {
    const { container } = render(
      renderWithProviders(
        <InlineBanner variant="info" icon={null}>
          No icon here
        </InlineBanner>,
      ),
    );
    expect(container.querySelector('svg')).toBeNull();
  });

  it('renders a dismiss control that calls onDismiss when clicked', async () => {
    const onDismiss = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <InlineBanner variant="info" onDismiss={onDismiss}>
          Dismiss me
        </InlineBanner>,
      ),
    );
    await user.click(screen.getByRole('button', { name: 'Закрыть' }));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('does not render a dismiss control when onDismiss is omitted', () => {
    render(renderWithProviders(<InlineBanner variant="info">No dismiss</InlineBanner>));
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('wires data-testid through', () => {
    render(
      renderWithProviders(
        <InlineBanner variant="warning" data-testid="my-banner">
          Testable
        </InlineBanner>,
      ),
    );
    expect(screen.getByTestId('my-banner')).toBeInTheDocument();
  });
});
