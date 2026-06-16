import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { httpClient } from '../httpClient';
import {
  downloadAuthenticatedFile,
  parseContentDispositionFilename,
} from '../downloadFile';

describe('parseContentDispositionFilename', () => {
  it('returns null for missing/empty header', () => {
    expect(parseContentDispositionFilename(undefined)).toBeNull();
    expect(parseContentDispositionFilename(null)).toBeNull();
    expect(parseContentDispositionFilename('attachment')).toBeNull();
  });

  it('parses the plain filename="..." form', () => {
    expect(
      parseContentDispositionFilename('attachment; filename="catalog.xlsx"'),
    ).toBe('catalog.xlsx');
  });

  it('parses an unquoted filename', () => {
    expect(parseContentDispositionFilename('attachment; filename=report.pdf')).toBe(
      'report.pdf',
    );
  });

  it('prefers the RFC 5987 extended filename* form and decodes it', () => {
    expect(
      parseContentDispositionFilename(
        "attachment; filename=\"fallback.csv\"; filename*=UTF-8''%D0%BE%D1%82%D1%87%D0%B5%D1%82.pdf",
      ),
    ).toBe('отчет.pdf');
  });
});

describe('downloadAuthenticatedFile', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>;
  let lastName: string | undefined;

  beforeEach(() => {
    lastName = undefined;
    clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        lastName = this.download;
      });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requests the path as a blob through httpClient and downloads with the header filename', async () => {
    const blob = new Blob(['data'], { type: 'application/pdf' });
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: blob,
      headers: { 'Content-Disposition': 'attachment; filename="server.pdf"' },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    await downloadAuthenticatedFile({
      path: '/exports/abc/download',
      fallbackFilename: 'export_abc',
    });

    expect(getSpy).toHaveBeenCalledWith('/exports/abc/download', {
      responseType: 'blob',
    });
    expect(clickSpy).toHaveBeenCalled();
    // Header (case-insensitive) wins over the fallback.
    expect(lastName).toBe('server.pdf');
  });

  it('derives the extension from the blob MIME type when no header is present', async () => {
    const blob = new Blob(['data'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: blob,
      headers: {},
    } as Awaited<ReturnType<typeof httpClient.get>>);

    await downloadAuthenticatedFile({
      path: '/exports/abc/download',
      fallbackFilename: 'export_abc',
    });

    expect(lastName).toBe('export_abc.xlsx');
  });
});
