import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { FileDropzone } from './FileDropzone';
import { formatBytes } from './formatBytes';
import { MAX_UPLOAD_BYTES, XLSX_MIME } from '../schemas/importSchemas';

function makeFile(name: string, size: number, type: string): File {
  const f = new File([new Uint8Array(size)], name, { type });
  // jsdom respects File but size may need an override; we trust File ctor here.
  Object.defineProperty(f, 'size', { value: size });
  return f;
}

describe('FileDropzone', () => {
  it('renders empty state when no file', () => {
    render(
      renderWithProviders(
        <FileDropzone file={null} onFileChange={() => {}} />,
      ),
    );
    expect(screen.getByTestId('file-dropzone-input')).toBeInTheDocument();
  });

  it('rejects files that exceed the 25 MB limit', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <FileDropzone file={null} onFileChange={onChange} />,
      ),
    );
    const input = screen.getByTestId('file-dropzone-input') as HTMLInputElement;
    const big = makeFile('big.xlsx', MAX_UPLOAD_BYTES + 1, XLSX_MIME);
    fireEvent.change(input, { target: { files: [big] } });
    expect(onChange).toHaveBeenCalledWith(null);
    expect(screen.getByTestId('file-dropzone-error')).toBeInTheDocument();
  });

  it('rejects non-.xlsx extensions', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <FileDropzone file={null} onFileChange={onChange} />,
      ),
    );
    const input = screen.getByTestId('file-dropzone-input') as HTMLInputElement;
    const csv = makeFile('foo.csv', 1024, 'text/csv');
    fireEvent.change(input, { target: { files: [csv] } });
    expect(onChange).toHaveBeenCalledWith(null);
    expect(screen.getByTestId('file-dropzone-error')).toBeInTheDocument();
  });

  it('rejects empty files', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <FileDropzone file={null} onFileChange={onChange} />,
      ),
    );
    const input = screen.getByTestId('file-dropzone-input') as HTMLInputElement;
    const empty = makeFile('empty.xlsx', 0, XLSX_MIME);
    fireEvent.change(input, { target: { files: [empty] } });
    expect(onChange).toHaveBeenCalledWith(null);
    expect(screen.getByTestId('file-dropzone-error')).toBeInTheDocument();
  });

  it('accepts a valid .xlsx file under 25 MB', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <FileDropzone file={null} onFileChange={onChange} />,
      ),
    );
    const input = screen.getByTestId('file-dropzone-input') as HTMLInputElement;
    const ok = makeFile('valid.xlsx', 4096, XLSX_MIME);
    fireEvent.change(input, { target: { files: [ok] } });
    expect(onChange).toHaveBeenCalledWith(ok);
  });

  it('rejects mismatched MIME types', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <FileDropzone file={null} onFileChange={onChange} />,
      ),
    );
    const input = screen.getByTestId('file-dropzone-input') as HTMLInputElement;
    const bad = makeFile('looks.xlsx', 2048, 'application/zip');
    fireEvent.change(input, { target: { files: [bad] } });
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it('formatBytes formats bytes/KB/MB', () => {
    expect(formatBytes(500)).toBe('500 B');
    expect(formatBytes(1500)).toMatch(/KB$/);
    expect(formatBytes(2 * 1024 * 1024)).toMatch(/MB$/);
  });
});
