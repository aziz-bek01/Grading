import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { SignedDownloadButton } from './SignedDownloadButton';

describe('SignedDownloadButton', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    // Suppress jsdom navigation when href is set.
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    clickSpy.mockRestore();
    vi.restoreAllMocks();
  });

  it('fetches a signed URL on click and triggers anchor download', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: { url: 'https://example.com/file.xlsx?sig=abc' },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(
      renderWithProviders(<SignedDownloadButton exportId="exp-1" filename="export.xlsx" />),
    );
    const btn = screen.getByTestId('signed-download-button').querySelector('button')!;
    fireEvent.click(btn);

    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledWith('/exports/exp-1/download-url');
    });
    expect(clickSpy).toHaveBeenCalled();
  });

  it('re-fetches the URL when the cached one is past its 60s TTL', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: { url: 'https://example.com/file.xlsx?sig=fresh' },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    const { getByTestId } = render(
      renderWithProviders(<SignedDownloadButton exportId="exp-2" />),
    );
    const btn = getByTestId('signed-download-button').querySelector('button')!;
    fireEvent.click(btn);
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(1));

    // Second click within TTL — reuses cached URL.
    fireEvent.click(btn);
    await waitFor(() => expect(clickSpy).toHaveBeenCalledTimes(2));
    expect(getSpy).toHaveBeenCalledTimes(1);
  });

  it('shows an error state when the fetch fails', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(new Error('network down'));
    render(renderWithProviders(<SignedDownloadButton exportId="exp-3" />));
    const btn = screen.getByTestId('signed-download-button').querySelector('button')!;
    fireEvent.click(btn);
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
  });
});
