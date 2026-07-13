import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { SignedDownloadButton } from './SignedDownloadButton';

/**
 * Generic tests for the shared button — feature-specific wiring (the real
 * httpClient download call, the exact error-key mapping per status code) is
 * covered end-to-end by `features/export/components/SignedDownloadButton.test.tsx`
 * and `features/report/components/ReportSignedDownloadButton.test.tsx`, which
 * now both render THIS component.
 */
describe('SignedDownloadButton (shared)', () => {
  it('renders the idle CTA, calls onDownload(id) on click, and shows no alert on success', async () => {
    const onDownload = vi.fn().mockResolvedValue(undefined);
    render(
      renderWithProviders(
        <SignedDownloadButton
          id="job-1"
          onDownload={onDownload}
          isDownloading={false}
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={() => 'export.download.error_v2'}
          testId="signed-download-button"
        />,
      ),
    );
    const btn = screen.getByTestId('signed-download-button').querySelector('button')!;
    // export.download.cta === 'Скачать' in ru-RU.
    expect(btn).toHaveTextContent('Скачать');

    fireEvent.click(btn);
    await waitFor(() => expect(onDownload).toHaveBeenCalledWith('job-1'));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('shows the downloading label and disables the button while isDownloading', () => {
    render(
      renderWithProviders(
        <SignedDownloadButton
          id="job-1"
          onDownload={vi.fn()}
          isDownloading
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={() => 'export.download.error_v2'}
          testId="signed-download-button"
        />,
      ),
    );
    const btn = screen.getByTestId('signed-download-button').querySelector('button')!;
    // export.download.downloading === 'Загрузка...' in ru-RU.
    expect(btn).toHaveTextContent('Загрузка...');
    expect(btn).toBeDisabled();
  });

  it('surfaces the localized error from resolveErrorKey when onDownload rejects', async () => {
    const err = new Error('boom');
    const onDownload = vi.fn().mockRejectedValue(err);
    const resolveErrorKey = vi.fn().mockReturnValue('export.download.error_forbidden');
    render(
      renderWithProviders(
        <SignedDownloadButton
          id="job-2"
          onDownload={onDownload}
          isDownloading={false}
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={resolveErrorKey}
          testId="signed-download-button"
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('signed-download-button').querySelector('button')!);
    await waitFor(() => {
      // export.download.error_forbidden === 'У вас нет прав на скачивание этого файла.'
      expect(screen.getByRole('alert')).toHaveTextContent(/нет прав/i);
    });
    expect(resolveErrorKey).toHaveBeenCalledWith(err);
  });

  it('clears a previous error on the next click', async () => {
    const onDownload = vi.fn().mockRejectedValueOnce(new Error('x')).mockResolvedValueOnce(undefined);
    render(
      renderWithProviders(
        <SignedDownloadButton
          id="job-3"
          onDownload={onDownload}
          isDownloading={false}
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={() => 'export.download.error_forbidden'}
          testId="signed-download-button"
        />,
      ),
    );
    const btn = screen.getByTestId('signed-download-button').querySelector('button')!;
    fireEvent.click(btn);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    fireEvent.click(btn);
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  it('shows the MSW demo-mode banner only when showMswWarning is set AND there is no error', () => {
    const { rerender } = render(
      renderWithProviders(
        <SignedDownloadButton
          id="job-4"
          onDownload={vi.fn()}
          isDownloading={false}
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={() => 'export.download.error_v2'}
          testId="signed-download-button"
          showMswWarning
          mswWarningKey="export.download.demo_warning"
        />,
      ),
    );
    // export.download.demo_warning === 'Демо-режим — файл сгенерирован локально'
    expect(screen.getByText(/Демо-режим/i)).toBeInTheDocument();

    rerender(
      renderWithProviders(
        <SignedDownloadButton
          id="job-4"
          onDownload={vi.fn()}
          isDownloading={false}
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={() => 'export.download.error_v2'}
          testId="signed-download-button"
        />,
      ),
    );
    expect(screen.queryByText(/Демо-режим/i)).toBeNull();
  });

  it('respects the disabled prop', () => {
    render(
      renderWithProviders(
        <SignedDownloadButton
          id="job-5"
          onDownload={vi.fn()}
          isDownloading={false}
          ctaKey="export.download.cta"
          downloadingKey="export.download.downloading"
          resolveErrorKey={() => 'export.download.error_v2'}
          testId="signed-download-button"
          disabled
        />,
      ),
    );
    expect(screen.getByTestId('signed-download-button').querySelector('button')).toBeDisabled();
  });
});
