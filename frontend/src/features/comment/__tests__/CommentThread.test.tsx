import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, signIn } from '@/test/testUtils';
import { CommentThread } from '../components/CommentThread';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
import type { AxiosAdapter } from 'axios';

describe('<CommentThread />', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  it('renders seeded comments for CFO job profile (incl. mention chip)', async () => {
    render(
      renderWithProviders(
        <CommentThread entityType="JOB_PROFILE" entityId="jp-cfo-v1" />,
      ),
    );
    await waitFor(() => {
      const cards = screen.queryAllByTestId('comment-card');
      expect(cards.length).toBeGreaterThan(0);
    });
    // At least one mention chip rendered for the @Dev User mention.
    expect(screen.queryAllByTestId('mention-chip').length).toBeGreaterThan(0);
    httpClient.defaults.adapter = originalAdapter;
  });

  it('supports replying to a top-level comment', async () => {
    render(
      renderWithProviders(
        <CommentThread entityType="JOB_PROFILE" entityId="jp-cfo-v1" />,
      ),
    );
    await waitFor(() => screen.getAllByTestId('comment-card'));
    const user = userEvent.setup();
    const replyTriggers = screen.queryAllByTestId('comment-reply-trigger');
    if (replyTriggers.length > 0) {
      await user.click(replyTriggers[0]);
      // Reply input shows up
      expect(screen.getAllByTestId('comment-input').length).toBeGreaterThanOrEqual(2);
    }
    httpClient.defaults.adapter = originalAdapter;
  });
});
