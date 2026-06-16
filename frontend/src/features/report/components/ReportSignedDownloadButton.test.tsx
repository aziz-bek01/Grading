import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { ReportSignedDownloadButton } from './ReportSignedDownloadButton';

describe('ReportSignedDownloadButton', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>;
  let createObjectUrlSpy: ReturnType<typeof vi.spyOn>;
  let lastDownloadName: string | undefined;

  beforeEach(() => {
    lastDownloadName = undefined;
    clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        lastDownloadName = this.download;
      });
    createObjectUrlSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock-report-url');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  afterEach(() => {
    clickSpy.mockRestore();
    createObjectUrlSpy.mockRestore();
    vi.restoreAllMocks();
  });

  it('streams the report through the auth client and downloads with the server filename', async () => {
    const blob = new Blob(['section,metric,value'], { type: 'text/csv;charset=utf-8' });
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: blob,
      headers: { 'content-disposition': 'attachment; filename="exec.pdf"' },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    render(
      renderWithProviders(
        <ReportSignedDownloadButton reportId="rep-1" reportType="EXECUTIVE_SUMMARY" />,
      ),
    );
    fireEvent.click(
      screen.getByTestId('report-signed-download-button').querySelector('button')!,
    );

    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledWith('/reports/rep-1/download', {
        responseType: 'blob',
      });
    });
    await waitFor(() => expect(clickSpy).toHaveBeenCalled());
    expect(createObjectUrlSpy).toHaveBeenCalledWith(blob);
    expect(lastDownloadName).toBe('exec.pdf');
  });

  it('surfaces a localized error (not a silent no-op) on 400 (not generated/expired)', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(400, { code: 'NOT_DOWNLOADABLE', message: 'expired' }),
    );
    render(renderWithProviders(<ReportSignedDownloadButton reportId="rep-2" />));
    fireEvent.click(
      screen.getByTestId('report-signed-download-button').querySelector('button')!,
    );
    // Tests run under the default ru-RU locale; assert the 400-specific copy.
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/не готов|истекла/i);
    });
    expect(clickSpy).not.toHaveBeenCalled();
  });
});
