import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { SignedDownloadButton } from './SignedDownloadButton';

/**
 * The download must flow through the authenticated httpClient (so the
 * Authorization + X-Active-Tenant-Id headers are attached) and trigger a
 * client-side object-URL download — NOT a bare `<a href>` navigation that
 * would omit the headers and silently fail.
 */
describe('SignedDownloadButton', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>;
  let createObjectUrlSpy: ReturnType<typeof vi.spyOn>;
  let revokeObjectUrlSpy: ReturnType<typeof vi.spyOn>;
  let lastDownloadName: string | undefined;

  beforeEach(() => {
    lastDownloadName = undefined;
    // Capture the `download` attribute at click time and suppress navigation.
    clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        lastDownloadName = this.download;
      });
    createObjectUrlSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock-object-url');
    revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  afterEach(() => {
    clickSpy.mockRestore();
    createObjectUrlSpy.mockRestore();
    revokeObjectUrlSpy.mockRestore();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('streams the file through the auth client and triggers an anchor download with the server filename', async () => {
    vi.useFakeTimers();
    const blob = new Blob(['col\nval'], { type: 'text/csv;charset=utf-8' });
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: blob,
      headers: { 'content-disposition': 'attachment; filename="catalog.xlsx"' },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(
      renderWithProviders(
        <SignedDownloadButton exportId="exp-1" exportType="POSITION_CATALOG" format="XLSX" />,
      ),
    );
    fireEvent.click(screen.getByTestId('signed-download-button').querySelector('button')!);

    await vi.waitFor(() => {
      // Goes through the SHARED httpClient (auth + tenant headers) as a blob.
      expect(getSpy).toHaveBeenCalledWith('/exports/exp-1/download', {
        responseType: 'blob',
      });
    });
    await vi.waitFor(() => expect(clickSpy).toHaveBeenCalled());

    expect(createObjectUrlSpy).toHaveBeenCalledWith(blob);
    // Filename comes from the Content-Disposition header, not the client.
    expect(lastDownloadName).toBe('catalog.xlsx');

    // Object URL is revoked after the click is processed.
    vi.advanceTimersByTime(1000);
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:mock-object-url');
  });

  it('falls back to <type>_<id> filename when no Content-Disposition header is present', async () => {
    const blob = new Blob(['x'], { type: 'text/csv;charset=utf-8' });
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: blob,
      headers: {},
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(
      renderWithProviders(
        <SignedDownloadButton exportId="exp-9" exportType="JOB_PROFILES" format="CSV" />,
      ),
    );
    fireEvent.click(screen.getByTestId('signed-download-button').querySelector('button')!);

    await waitFor(() => expect(clickSpy).toHaveBeenCalled());
    expect(lastDownloadName).toBe('job_profiles_exp-9.csv');
  });

  it('surfaces a localized error (not a silent no-op) on 403', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(403, { code: 'FORBIDDEN', message: 'no salary export' }),
    );
    render(renderWithProviders(<SignedDownloadButton exportId="exp-3" />));
    fireEvent.click(screen.getByTestId('signed-download-button').querySelector('button')!);
    // Tests run under the default ru-RU locale; assert the 403-specific copy.
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/нет прав/i);
    });
    // No download attempted.
    expect(clickSpy).not.toHaveBeenCalled();
  });

  it('surfaces a localized error on 404', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(404, { code: 'NOT_FOUND', message: 'gone' }),
    );
    render(renderWithProviders(<SignedDownloadButton exportId="exp-4" />));
    fireEvent.click(screen.getByTestId('signed-download-button').querySelector('button')!);
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/не найден/i);
    });
  });
});
